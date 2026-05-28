import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);

function readRelative(relativePath) {
  return readFileSync(path.join(repositoryRoot, relativePath), "utf8");
}

function requireMatch(label, text, pattern) {
  const match = text.match(pattern);
  if (!match?.[1]) {
    throw new Error(`Could not find ${label}.`);
  }
  return match[1];
}

function assertEqual(label, actual, expected) {
  if (actual !== expected) {
    throw new Error(
      `${label} mismatch: expected ${expected}, found ${actual}.`,
    );
  }
}

const backendPom = readRelative("backend/pom.xml");
const backendDockerfile = readRelative("backend/Dockerfile");
const mavenWrapperProperties = readRelative(
  "backend/.mvn/wrapper/maven-wrapper.properties",
);
const frontendDockerfile = readRelative("frontend/Dockerfile");
const workflow = readRelative(".github/workflows/repository-quality-gate.yml");

const javaVersion = requireMatch(
  "backend pom java.version",
  backendPom,
  /<java\.version>([^<]+)<\/java\.version>/,
);
const mavenVersion = requireMatch(
  "backend pom maven.version",
  backendPom,
  /<maven\.version>([^<]+)<\/maven\.version>/,
);
const wrapperMavenVersion = requireMatch(
  "Maven wrapper distribution version",
  mavenWrapperProperties,
  /apache-maven\/([^/]+)\/apache-maven-\1-bin\.zip/,
);
const dockerMavenVersion = requireMatch(
  "backend Dockerfile Maven version",
  backendDockerfile,
  /^FROM maven:([^-]+)-eclipse-temurin-\d+ AS build$/m,
);
const dockerJavaVersion = requireMatch(
  "backend Dockerfile Java version",
  backendDockerfile,
  /^FROM maven:[^-]+-eclipse-temurin-(\d+) AS build$/m,
);
const dockerRuntimeJavaVersion = requireMatch(
  "backend Dockerfile runtime Java version",
  backendDockerfile,
  /^FROM eclipse-temurin:(\d+)-jre-alpine$/m,
);
const workflowJavaVersion = requireMatch(
  "GitHub Actions Java version",
  workflow,
  /java-version:\s*(\d+)/,
);
const frontendNodeVersion = requireMatch(
  "frontend Dockerfile Node version",
  frontendDockerfile,
  /^FROM node:(\d+)-alpine AS build$/m,
);
const workflowNodeVersion = requireMatch(
  "GitHub Actions Node version",
  workflow,
  /node-version:\s*(\d+)/,
);

assertEqual("Maven wrapper version", wrapperMavenVersion, mavenVersion);
assertEqual(
  "Backend Dockerfile Maven version",
  dockerMavenVersion,
  mavenVersion,
);
assertEqual(
  "Backend Dockerfile build Java version",
  dockerJavaVersion,
  javaVersion,
);
assertEqual(
  "Backend Dockerfile runtime Java version",
  dockerRuntimeJavaVersion,
  javaVersion,
);
assertEqual("GitHub Actions Java version", workflowJavaVersion, javaVersion);
assertEqual(
  "GitHub Actions Node version",
  workflowNodeVersion,
  frontendNodeVersion,
);

console.log(
  `Toolchain versions aligned: Java ${javaVersion}, Maven ${mavenVersion}, Node ${frontendNodeVersion}.`,
);
