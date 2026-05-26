import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";
import net from "node:net";

const repositoryRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);
const backendDirectory = path.join(repositoryRoot, "backend");
const isWindows = process.platform === "win32";
const mavenWrapperCommand = isWindows
  ? process.env.ComSpec || "cmd.exe"
  : "./mvnw";

function findAvailablePort(preferredPort, excludedPorts = new Set()) {
  return new Promise((resolve, reject) => {
    const resolveIfAllowed = (port) => {
      if (!excludedPorts.has(port)) {
        resolve(port);
        return true;
      }

      return false;
    };

    const server = net.createServer();
    server.unref();
    server.on("error", tryFallbackPort);
    if (excludedPorts.has(preferredPort)) {
      tryFallbackPort();
      return;
    }

    function tryFallbackPort() {
      const fallback = net.createServer();
      fallback.unref();
      fallback.on("error", reject);
      fallback.listen(0, "127.0.0.1", () => {
        const address = fallback.address();
        fallback.close(() => {
          if (typeof address === "object" && address?.port) {
            if (resolveIfAllowed(address.port)) return;
            findAvailablePort(0, excludedPorts).then(resolve, reject);
          } else {
            reject(new Error("Could not allocate an available TCP port."));
          }
        });
      });
    }

    server.listen(preferredPort, "127.0.0.1", () => {
      const address = server.address();
      server.close(() => {
        if (
          typeof address === "object" &&
          address?.port &&
          resolveIfAllowed(address.port)
        )
          return;
        tryFallbackPort();
      });
    });
  });
}

const openApiContractPort = await findAvailablePort(18080);
const openApiContractJmxPort = await findAvailablePort(
  19001,
  new Set([openApiContractPort]),
);

const steps = [
  ["Backend tests", ["test"]],
  ["Backend static analysis", ["spotbugs:check"]],
  ["Backend coverage gate", ["verify"]],
  [
    "Backend OpenAPI contract export",
    [
      "-DskipTests",
      "-Djacoco.skip=true",
      "-DskipOpenApiContract=false",
      `-DopenApiContractPort=${openApiContractPort}`,
      `-DopenApiContractJmxPort=${openApiContractJmxPort}`,
      "verify",
    ],
  ],
];

for (const [label, args] of steps) {
  console.log(`\n==> ${label}`);
  const mavenArgs = ["-B", ...args];
  const commandArgs = isWindows
    ? ["/d", "/s", "/c", "mvnw.cmd", ...mavenArgs]
    : mavenArgs;
  const result = spawnSync(mavenWrapperCommand, commandArgs, {
    cwd: backendDirectory,
    stdio: "inherit",
  });

  if (result.status !== 0) {
    const status = result.status ?? "unknown";
    console.error(`\n${label} failed with exit status ${status}.`);
    process.exit(typeof result.status === "number" ? result.status : 1);
  }
}
