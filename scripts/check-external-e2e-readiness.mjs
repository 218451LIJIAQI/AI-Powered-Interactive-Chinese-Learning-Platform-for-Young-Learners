import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
const backendDirectory = path.join(repositoryRoot, "backend");

const envFiles = [
  path.join(repositoryRoot, ".env.external-e2e"),
  path.join(backendDirectory, ".env.ai.local"),
  path.join(backendDirectory, ".env.smtp.local"),
].filter(existsSync);

for (const envFile of envFiles) {
  loadEnvFile(envFile);
}

const findings = [];

function loadEnvFile(filePath) {
  const contents = readFileSync(filePath, "utf8");
  for (const rawLine of contents.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;

    const separatorIndex = line.indexOf("=");
    if (separatorIndex <= 0) continue;

    const name = line.slice(0, separatorIndex).trim();
    let value = line.slice(separatorIndex + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    if (process.env[name] === undefined) {
      process.env[name] = value;
    }
  }
}

function value(name) {
  return process.env[name]?.trim() ?? "";
}

function isPlaceholder(rawValue) {
  const normalized = rawValue.trim().toLowerCase();
  return (
    !normalized ||
    normalized === "replace-me" ||
    normalized.startsWith("replace-with-") ||
    normalized.startsWith("your-") ||
    (normalized.startsWith("<") && normalized.endsWith(">")) ||
    normalized.includes("placeholder")
  );
}

function requireValue(group, name) {
  const rawValue = value(name);
  if (isPlaceholder(rawValue)) {
    findings.push(`${group}: ${name} is missing or still a placeholder.`);
    return "";
  }
  return rawValue;
}

function requireBoolean(group, name, expected) {
  const rawValue = requireValue(group, name);
  if (!rawValue) return;

  if (rawValue.toLowerCase() !== String(expected)) {
    findings.push(`${group}: ${name} must be ${expected}.`);
  }
}

function requireHttpsUrl(group, name, { allowLocalhost = false } = {}) {
  const rawValue = requireValue(group, name);
  if (!rawValue) return;

  try {
    const url = new URL(rawValue);
    const host = url.hostname.toLowerCase();
    const isLocalhost =
      host === "localhost" || host === "127.0.0.1" || host === "::1";
    if (
      url.protocol !== "https:" &&
      !(allowLocalhost && url.protocol === "http:" && isLocalhost)
    ) {
      findings.push(
        `${group}: ${name} must use HTTPS${allowLocalhost ? " unless it targets localhost" : ""}.`,
      );
    }
  } catch {
    findings.push(`${group}: ${name} must be an absolute URL.`);
  }
}

function requirePrefix(group, name, prefixes) {
  const rawValue = requireValue(group, name);
  if (!rawValue) return;

  if (!prefixes.some((prefix) => rawValue.startsWith(prefix))) {
    findings.push(
      `${group}: ${name} must start with ${prefixes.join(" or ")}.`,
    );
  }
}

function checkAi() {
  const group = "AI real-provider E2E";
  requireBoolean(group, "AI_ENABLE_REAL_PROVIDER", true);
  const provider = requireValue(group, "AI_PROVIDER").toLowerCase();
  if (
    provider &&
    !["deepseek", "openai-compatible", "openai_compatible"].includes(provider)
  ) {
    findings.push(
      `${group}: AI_PROVIDER must be deepseek or openai-compatible for the current backend client.`,
    );
  }
  requireHttpsUrl(group, "AI_API_BASE_URL", { allowLocalhost: true });
  requireValue(group, "AI_API_KEY");
  requireValue(group, "AI_MODEL_NAME");
  requireValue(group, "EXTERNAL_E2E_AI_TEST_PROMPT");
}

function checkStripe() {
  const group = "Stripe test-mode E2E";
  const provider = requireValue(group, "PAYMENT_PROVIDER").toLowerCase();
  if (provider && provider !== "stripe") {
    findings.push(`${group}: PAYMENT_PROVIDER must be stripe.`);
  }

  const key = requireValue(group, "STRIPE_API_KEY");
  if (key && !(key.startsWith("sk_test_") || key.startsWith("rk_test_"))) {
    findings.push(
      `${group}: STRIPE_API_KEY must be a test-mode key (sk_test_ or rk_test_) for local real-provider E2E.`,
    );
  }

  requirePrefix(group, "STRIPE_WEBHOOK_SECRET", ["whsec_"]);
  requireHttpsUrl(group, "PAYMENT_SUCCESS_URL", { allowLocalhost: true });
  requireHttpsUrl(group, "PAYMENT_CANCEL_URL", { allowLocalhost: true });
  requireValue(group, "EXTERNAL_E2E_STRIPE_WEBHOOK_FORWARDING");
}

function checkSmtp() {
  const group = "SMTP real-delivery E2E";
  requireBoolean(group, "SMTP_ENABLED", true);
  requireValue(group, "SMTP_HOST");
  requireValue(group, "SMTP_PORT");

  const authEnabled = value("SMTP_AUTH_ENABLED").toLowerCase() !== "false";
  if (authEnabled) {
    requireValue(group, "SMTP_USERNAME");
    requireValue(group, "SMTP_PASSWORD");
  }

  requireValue(group, "SMTP_FROM");
  requireHttpsUrl(group, "PASSWORD_RESET_BASE_URL", { allowLocalhost: true });
  requireValue(group, "EXTERNAL_E2E_SMTP_TEST_RECIPIENT");
}

checkAi();
checkStripe();
checkSmtp();

if (findings.length > 0) {
  console.error("External provider E2E readiness failed.");
  console.error(
    "Create .env.external-e2e or export the required variables, then run this precheck before live AI/Stripe/SMTP smoke.",
  );
  for (const finding of findings) {
    console.error(`- ${finding}`);
  }
  process.exit(1);
}

console.log(
  "External provider E2E readiness passed: AI, Stripe test-mode, and SMTP variables are present and structurally valid.",
);
