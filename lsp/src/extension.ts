import * as path from "path";
import * as os from "os";
import { workspace, ExtensionContext } from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
} from "vscode-languageclient/node";

let client: LanguageClient;

export function activate(context: ExtensionContext) {
  const config = workspace.getConfiguration("mira");
  const jarPath =
    config.get<string>("jarPath") ||
    path.join(os.homedir(), ".mira", "mira.jar");

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
}

export function deactivate(): Thenable<void> | undefined {
  return client?.stop();
}
