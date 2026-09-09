import * as path from "path";
import * as os from "os";
import * as fs from "fs";
import {
  workspace,
  ExtensionContext,
  debug,
  DebugAdapterExecutable,
  DebugAdapterTracker,
  DebugAdapterTrackerFactory,
  DebugConfiguration,
  DebugSession,
  languages,
  CodeLensProvider,
  CodeLens,
  TextDocument,
  Range,
  Position,
  Location,
  commands,
  Uri,
  window,
} from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
} from "vscode-languageclient/node";

let client: LanguageClient;

function getJarPath(): string {
  const config = workspace.getConfiguration("mira");
  return (
    config.get<string>("jarPath") ||
    path.join(os.homedir(), ".mira", "mira.jar")
  );
}

interface LspPosition {
  line: number;
  character: number;
}

interface LspLocation {
  uri: string;
  range: { start: LspPosition; end: LspPosition };
}

function toVscodePosition(p: LspPosition): Position {
  return new Position(p.line, p.character);
}

function toVscodeLocation(loc: LspLocation): Location {
  return new Location(
    Uri.parse(loc.uri),
    new Range(
      toVscodePosition(loc.range.start),
      toVscodePosition(loc.range.end),
    ),
  );
}

class MiraCodeLensProvider implements CodeLensProvider {
  provideCodeLenses(document: TextDocument): CodeLens[] {
    const lenses: CodeLens[] = [];
    const lines = document.getText().split("\n");

    let targetLine = 0;
    let hasMain = false;
    for (let i = 0; i < lines.length; i++) {
      if (/^\s*fn\s+main\s*\(/.test(lines[i])) {
        targetLine = i;
        hasMain = true;
        break;
      }
    }
    const range = new Range(targetLine, 0, targetLine, 0);
    lenses.push(
      new CodeLens(range, {
        title: "▶ Run",
        command: "mira.run",
        arguments: [document.uri, hasMain],
      }),
    );
    lenses.push(
      new CodeLens(range, {
        title: "⬡ Debug",
        command: "mira.debug",
        arguments: [document.uri],
      }),
    );
    return lenses;
  }
}

const traceFile = path.join(os.homedir(), ".mira", "dap-trace.txt");

class MiraTrackerFactory implements DebugAdapterTrackerFactory {
  createDebugAdapterTracker(_session: DebugSession): DebugAdapterTracker {
    const log = (line: string) => {
      try {
        fs.appendFileSync(traceFile, line + "\n");
      } catch {}
    };
    return {
      onWillStartSession: () =>
        log(`\n=== SESSION START ${new Date().toISOString()} ===`),
      onWillReceiveMessage: (msg) =>
        log(`${new Date().toISOString()} → ${JSON.stringify(msg)}`),
      onDidSendMessage: (msg) =>
        log(`${new Date().toISOString()} ← ${JSON.stringify(msg)}`),
      onError: (err) => log(`ERROR: ${err}`),
      onWillStopSession: () => log("=== SESSION STOP ==="),
    };
  }
}

export function activate(context: ExtensionContext) {
  const jarPath = getJarPath();

  const serverOptions: ServerOptions = {
    command: "java",
    args: ["-jar", jarPath, "--lsp"],
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", language: "mira" }],
    synchronize: {
      fileEvents: workspace.createFileSystemWatcher("**/*.mira"),
    },
  };

  client = new LanguageClient(
    "mira",
    "Mira Language Server",
    serverOptions,
    clientOptions,
  );

  client.start();
  context.subscriptions.push(client);

  context.subscriptions.push(
    debug.registerDebugAdapterTrackerFactory("mira", new MiraTrackerFactory()),
  );

  const factory = debug.registerDebugAdapterDescriptorFactory("mira", {
    createDebugAdapterDescriptor(
      _session: DebugSession,
      _executable: DebugAdapterExecutable | undefined,
    ) {
      return new DebugAdapterExecutable("java", [
        "-jar",
        getJarPath(),
        "--dap",
      ]);
    },
  });
  context.subscriptions.push(factory);

  context.subscriptions.push(
    commands.registerCommand("mira.run", (uri?: Uri, hasMain?: boolean) => {
      const fileUri = uri ?? window.activeTextEditor?.document.uri;
      if (!fileUri) {
        window.showErrorMessage("No Mira file open.");
        return;
      }
      const flags = hasMain ? " -m" : "";
      const terminal = window.createTerminal("Mira Run");
      terminal.show();
      terminal.sendText(
        `java -jar "${getJarPath()}" "${fileUri.fsPath}"${flags}`,
      );
    }),
    commands.registerCommand(
      "mira.showReferences",
      (uri: string, position: LspPosition, locations: LspLocation[]) => {
        commands.executeCommand(
          "editor.action.showReferences",
          Uri.parse(uri),
          toVscodePosition(position),
          (locations ?? []).map(toVscodeLocation),
        );
      },
    ),
    commands.registerCommand("mira.runTests", (uri?: Uri | string) => {
      const fileUri =
        typeof uri === "string"
          ? Uri.parse(uri)
          : (uri ?? window.activeTextEditor?.document.uri);
      if (!fileUri) {
        window.showErrorMessage("No Mira file open.");
        return;
      }
      const terminal = window.createTerminal("Mira Test");
      terminal.show();
      terminal.sendText(
        `java -jar "${getJarPath()}" "${fileUri.fsPath}" --test`,
      );
    }),
    commands.registerCommand("mira.debug", async (uri?: Uri) => {
      const fileUri = uri ?? window.activeTextEditor?.document.uri;
      if (!fileUri) {
        window.showErrorMessage("No Mira file open.");
        return;
      }
      const folder =
        workspace.getWorkspaceFolder(fileUri) ??
        workspace.workspaceFolders?.[0];
      const started = await debug.startDebugging(folder, {
        type: "mira",
        request: "launch",
        name: "Debug Mira File",
        program: fileUri.fsPath,
      } as DebugConfiguration);
      if (!started) {
        window.showErrorMessage(
          "Failed to start Mira debug session. Make sure mira.jar is installed at ~/.mira/mira.jar",
        );
      }
    }),
    languages.registerCodeLensProvider(
      { language: "mira" },
      new MiraCodeLensProvider(),
    ),
  );
}

export function deactivate(): Thenable<void> | undefined {
  return client?.stop();
}
