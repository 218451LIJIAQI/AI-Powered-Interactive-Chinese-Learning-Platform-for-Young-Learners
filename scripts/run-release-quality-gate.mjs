import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
const isWindows = process.platform === "win32";
const windowsCommand = process.env.ComSpec || "cmd.exe";

function run(label, command, args, options = {}) {
  console.log(`\n==> ${label}`);
  const result = spawnSync(command, args, {
    cwd: repositoryRoot,
    stdio: "inherit",
    shell: false,
    ...options,
  });

  if (result.status !== 0) {
    const status = result.status ?? "unknown";
    throw new Error(`${label} failed with exit status ${status}.`);
  }
}

function runNpm(label, args) {
  if (isWindows) {
    run(label, windowsCommand, ["/d", "/s", "/c", "npm", ...args]);
    return;
  }

  run(label, "npm", args);
}

function commandExists(command) {
  const probe = isWindows ? "where.exe" : "command";
  const args = isWindows ? [command] : ["-v", command];
  const result = spawnSync(probe, args, { stdio: "ignore", shell: !isWindows });
  return result.status === 0;
}

function writeComposeEnvFile(directory) {
  const filePath = path.join(directory, "quality-gate.prod.env");
  writeFileSync(
    filePath,
    [
      "DB_ROOT_PASSWORD=quality-gate-root-password",
      "DB_PASSWORD=quality-gate-application-password",
      "JWT_SECRET=quality-gate-jwt-secret-with-more-than-32-characters",
      "FRONTEND_BASE_URL=https://learn.example.com",
      "APP_CORS_ALLOWED_ORIGINS=https://learn.example.com",
      "AI_API_KEY=quality-gate-ai-key",
      "STRIPE_API_KEY=quality-gate-stripe-key-placeholder",
      "STRIPE_WEBHOOK_SECRET=quality-gate-stripe-webhook-placeholder",
      "PAYMENT_SUCCESS_URL=https://learn.example.com/payment/success",
      "PAYMENT_CANCEL_URL=https://learn.example.com/payment/cancel",
      "SMTP_HOST=smtp.example.com",
      "PASSWORD_RESET_BASE_URL=https://learn.example.com/reset-password",
      "VITE_API_BASE_URL=/api/v1",
      "",
    ].join("\n"),
    "utf8",
  );
  return filePath;
}

function validateComposeConfig() {
  const tempDirectory = mkdtempSync(
    path.join(os.tmpdir(), "mandarin-spark-release-"),
  );
  try {
    const envFile = writeComposeEnvFile(tempDirectory);
    run("Production Compose config", "docker", [
      "compose",
      "--env-file",
      envFile,
      "-f",
      "docker-compose.prod.yml",
      "config",
    ]);
    run("Local backend Compose config", "docker", [
      "compose",
      "-f",
      "backend/docker-compose.yml",
      "config",
    ]);
  } finally {
    rmSync(tempDirectory, { recursive: true, force: true });
  }
}

function validateFlywayMySql() {
  if (isWindows) {
    run("Flyway MySQL validation", "powershell", [
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-File",
      "backend/scripts/validate-flyway-mysql.ps1",
    ]);
    return;
  }

  if (!commandExists("pwsh")) {
    throw new Error(
      "PowerShell (`pwsh`) is required to run backend/scripts/validate-flyway-mysql.ps1.",
    );
  }

  run("Flyway MySQL validation", "pwsh", [
    "-NoProfile",
    "-ExecutionPolicy",
    "Bypass",
    "-File",
    "backend/scripts/validate-flyway-mysql.ps1",
  ]);
}

try {
  if (!commandExists("trivy")) {
    throw new Error(
      "Trivy is required for the release quality gate. Install Trivy or use CI, where aquasecurity/trivy-action runs the same HIGH/CRITICAL filesystem scan.",
    );
  }

  runNpm("Repository quality gate", ["run", "quality"]);
  runNpm("Frontend browser E2E smoke", ["run", "quality:frontend:e2e"]);
  runNpm("Root production dependency audit", [
    "audit",
    "--audit-level=high",
    "--omit=dev",
  ]);
  runNpm("Frontend production dependency audit", [
    "--prefix",
    "frontend",
    "audit",
    "--audit-level=high",
    "--omit=dev",
  ]);
  validateComposeConfig();
  validateFlywayMySql();
  run("Trivy filesystem scan", "trivy", [
    "fs",
    "--severity",
    "HIGH,CRITICAL",
    "--ignore-unfixed",
    "--exit-code",
    "1",
    ".",
  ]);
} catch (error) {
  console.error(`\n${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
