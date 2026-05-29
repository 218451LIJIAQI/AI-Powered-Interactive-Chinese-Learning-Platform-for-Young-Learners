import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function readRelative(relativePath) {
  return readFileSync(path.join(repositoryRoot, relativePath), 'utf8')
}

function requireMatch(label, text, pattern) {
  const match = text.match(pattern)
  if (!match?.[1]) {
    throw new Error(`Could not find ${label}.`)
  }
  return match[1]
}

function assertEqual(label, actual, expected) {
  if (actual !== expected) {
    throw new Error(`${label} mismatch: expected ${expected}, found ${actual}.`)
  }
}

function parseJson(label, text) {
  try {
    return JSON.parse(text)
  } catch (error) {
    throw new Error(`Could not parse ${label}.`, { cause: error })
  }
}

function assertNodeMajor(label, version, expectedMajor) {
  const normalized = String(version).trim().replace(/^v/, '')
  const major = normalized.split('.', 1)[0]
  assertEqual(label, major, expectedMajor)
}

const backendPom = readRelative('backend/pom.xml')
const backendDockerfile = readRelative('backend/Dockerfile')
const rootPackageJson = parseJson('root package.json', readRelative('package.json'))
const frontendPackageJson = parseJson('frontend package.json', readRelative('frontend/package.json'))
const mavenWrapperProperties = readRelative('backend/.mvn/wrapper/maven-wrapper.properties')
const frontendDockerfile = readRelative('frontend/Dockerfile')
const workflow = readRelative('.github/workflows/repository-quality-gate.yml')
const nvmrc = readRelative('.nvmrc').trim()
const nodeVersionFile = readRelative('.node-version').trim()

const javaVersion = requireMatch('backend pom java.version', backendPom, /<java\.version>([^<]+)<\/java\.version>/)
const mavenVersion = requireMatch('backend pom maven.version', backendPom, /<maven\.version>([^<]+)<\/maven\.version>/)
const wrapperMavenVersion = requireMatch(
  'Maven wrapper distribution version',
  mavenWrapperProperties,
  /apache-maven\/([^/]+)\/apache-maven-\1-bin\.zip/,
)
const dockerMavenVersion = requireMatch(
  'backend Dockerfile Maven version',
  backendDockerfile,
  /^FROM maven:([^-]+)-eclipse-temurin-\d+ AS build$/m,
)
const dockerJavaVersion = requireMatch(
  'backend Dockerfile Java version',
  backendDockerfile,
  /^FROM maven:[^-]+-eclipse-temurin-(\d+) AS build$/m,
)
const dockerRuntimeJavaVersion = requireMatch(
  'backend Dockerfile runtime Java version',
  backendDockerfile,
  /^FROM eclipse-temurin:(\d+)-jre-alpine$/m,
)
const workflowJavaVersion = requireMatch('GitHub Actions Java version', workflow, /java-version:\s*(\d+)/)
const frontendNodeVersion = requireMatch(
  'frontend Dockerfile Node version',
  frontendDockerfile,
  /^FROM node:(\d+)-alpine AS build$/m,
)
const workflowNodeVersion = requireMatch('GitHub Actions Node version', workflow, /node-version:\s*(\d+)/)

assertEqual('Maven wrapper version', wrapperMavenVersion, mavenVersion)
assertEqual('Backend Dockerfile Maven version', dockerMavenVersion, mavenVersion)
assertEqual('Backend Dockerfile build Java version', dockerJavaVersion, javaVersion)
assertEqual('Backend Dockerfile runtime Java version', dockerRuntimeJavaVersion, javaVersion)
assertEqual('GitHub Actions Java version', workflowJavaVersion, javaVersion)
assertEqual('GitHub Actions Node version', workflowNodeVersion, frontendNodeVersion)
assertEqual('root package engines.node', rootPackageJson.engines?.node, '>=22 <23')
assertEqual('frontend package engines.node', frontendPackageJson.engines?.node, '>=22 <23')
assertEqual('.nvmrc Node version', nvmrc, frontendNodeVersion)
assertEqual('.node-version Node version', nodeVersionFile, frontendNodeVersion)
assertNodeMajor('current Node.js runtime major', process.versions.node, frontendNodeVersion)

console.log(
  `Toolchain versions aligned: Java ${javaVersion}, Maven ${mavenVersion}, Node ${frontendNodeVersion} (current ${process.versions.node}).`,
)
