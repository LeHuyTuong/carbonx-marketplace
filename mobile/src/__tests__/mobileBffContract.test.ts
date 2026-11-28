import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const mobileSrcRoot = path.resolve(__dirname, "..");
const repoRoot = path.resolve(mobileSrcRoot, "../..");

function collectTsFiles(dir: string): string[] {
  if (!fs.existsSync(dir)) return [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const full = path.join(dir, entry.name);
    return entry.isDirectory() ? collectTsFiles(full) : full.endsWith(".ts") ? [full] : [];
  });
}

function extractImportSpecifiers(source: string): string[] {
  const specs: string[] = [];
  const regex = /(?:import|export)\s+(?:type\s+)?[\s\S]*?from\s+["']([^"']+)["']/g;
  for (const match of source.matchAll(regex)) {
    specs.push(match[1]);
  }
  return specs;
}

test("DDD Clean Boundary: domain & application layers must not import infrastructure or presentation", () => {
  const domainFiles = collectTsFiles(path.join(mobileSrcRoot, "domain"));
  const appFiles = collectTsFiles(path.join(mobileSrcRoot, "application"));

  assert.ok(domainFiles.length > 0, "Expected mobile domain contracts");
  assert.ok(appFiles.length >= 3, "Expected mobile CQRS input & outbound ports");

  for (const file of domainFiles) {
    const specs = extractImportSpecifiers(fs.readFileSync(file, "utf8"));
    for (const spec of specs) {
      assert.doesNotMatch(
        spec,
        /application\/|infrastructure\/|presentation\/|@application|@infrastructure|@presentation|^expo-|^react-native/,
        `Domain file violated Clean boundary (${spec}): ${file}`,
      );
    }
  }

  for (const file of appFiles) {
    const specs = extractImportSpecifiers(fs.readFileSync(file, "utf8"));
    for (const spec of specs) {
      assert.doesNotMatch(
        spec,
        /infrastructure\/|presentation\/|@infrastructure|@presentation|^expo-|^react-native/,
        `Application port violated Clean boundary (${spec}): ${file}`,
      );
    }
  }
});

test("BFF Contract Parity: Mobile TS CQRS ports match Java MobileBff use-case methods 1-to-1", () => {
  const javaQueryPath = path.join(
    repoRoot,
    "backend/mobile-bff/src/main/java/com/carbonx/mobilebff/application/port/in/MobileBffQueryUseCase.java",
  );
  const javaCmdPath = path.join(
    repoRoot,
    "backend/mobile-bff/src/main/java/com/carbonx/mobilebff/application/port/in/MobileBffCommandUseCase.java",
  );
  const tsQueryPath = path.join(mobileSrcRoot, "application/port/in/MobileEvHomeQueryPort.ts");
  const tsCmdPath = path.join(mobileSrcRoot, "application/port/in/MobileEvActionsCommandPort.ts");

  const javaQuery = fs.readFileSync(javaQueryPath, "utf8");
  const javaCmd = fs.readFileSync(javaCmdPath, "utf8");
  const tsQuery = fs.readFileSync(tsQueryPath, "utf8");
  const tsCmd = fs.readFileSync(tsCmdPath, "utf8");

  for (const queryMethod of ["getEvOwnerHomeBootstrap", "getCursorPayoutProvenanceFeed", "listMyCompactVehicles"]) {
    assert.match(javaQuery, new RegExp(queryMethod), `Missing in Java MobileBffQueryUseCase: ${queryMethod}`);
    assert.match(tsQuery, new RegExp(queryMethod), `Missing in TS MobileEvHomeQueryPort: ${queryMethod}`);
  }

  for (const cmdMethod of [
    "requestCameraUploadUrl",
    "submitMobileVehicleRegistration",
    "registerMobilePushDevice",
    "revokeMobilePushDevice",
    "requestMobileWithdrawal",
  ]) {
    assert.match(javaCmd, new RegExp(cmdMethod), `Missing in Java MobileBffCommandUseCase: ${cmdMethod}`);
    assert.match(tsCmd, new RegExp(cmdMethod), `Missing in TS MobileEvActionsCommandPort: ${cmdMethod}`);
  }
});
