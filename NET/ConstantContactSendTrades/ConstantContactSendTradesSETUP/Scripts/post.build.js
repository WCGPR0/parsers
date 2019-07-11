var project =
{
    directory: WScript.Arguments(0),
    configuration: WScript.Arguments(1),
    revision: WScript.Arguments(2),
    output:
    {
        directory: WScript.Arguments(3),
        fileName: WScript.Arguments(4),
        fullPath: function () { return this.directory + this.fileName; }
    },
    installType: WScript.Arguments(5)
}

var fileSystem = WScript.CreateObject("Scripting.FileSystemObject");

function createUpdate(specification, packageName, version) {
    if (specification != null) {
        var updateSettings = WScript.CreateObject("MSXML2.DOMDocument.6.0")
        if (updateSettings.loadXML(specification.xml)) {
            var packageURL = updateSettings.documentElement.getAttribute("package").replace("$(package.name)", packageName);
            updateSettings.documentElement.setAttribute("version", version);
            updateSettings.documentElement.setAttribute("package", packageURL);
            reader.parse(updateSettings);
            var output = writer.output;
            //WScript.echo(output);
            var tempDir = fileSystem.getSpecialFolder(2);
            //WScript.echo(tempDir);
            var textStream = fileSystem.CreateTextFile(tempDir + "\\update.xml", true);
            textStream.Write(output);
            textStream.Close();
            return tempDir + "\\update.xml";
        }
    }
    return "";
}

WScript.echo("== Starting Post-Build script for Wix.Local ==");
WScript.echo("\t");

WScript.echo("== Starting Deployment for " + project.configuration + " ==");
WScript.echo("\t");

if (fileSystem.FileExists(project.output.fullPath())) {
    WScript.echo("Output revision '" + project.revision + "', package '" + project.output.fullPath() + "'.");
    var deployment = WScript.CreateObject("MSXML2.DOMDocument.6.0");
    if (deployment.load(project.directory + "\\Configuration\\" + project.configuration + ".deploy.xml")) {
        var msi = fileSystem.getFile(project.output.fullPath());
        var deployments = deployment.documentElement.selectNodes("//Deploy");

        WScript.echo(deployments.length + " Deployment" + (deployments.length == 1 ? "" : "s") + " found.\t")
        for (var i = 0; i < deployments.length; i++) {
            var completed = false;
            var deploy = deployments[i];
            var deployLocation = deploy.selectSingleNode("Location");
            var deployUpdate = deploy.selectSingleNode("Update");
            var deployType = deploy.getAttribute("type");
            if (deployType == null || deployType == "") deployType = "local";
            var deployTo = deploy.getAttribute("to");
            if (deployTo == null || deployTo == "") deployTo = "unspecified";

            WScript.echo("\t");
            WScript.echo(project.configuration + " '" + deployType + "' Deployment to '" + deployTo + "' initiated");

            var writer = WScript.CreateObject("MSXML2.MXXMLWriter");
            var reader = WScript.CreateObject("MSXML2.SAXXMLReader");
            writer.indent = true;
            writer.omitXMLDeclaration = true;
            reader.contentHandler = writer;

            switch (deployType) {
                case "custom":
                case "winscp.com":
                    var deployCommands = deploy.selectNodes("Command");
                    var shell = WScript.CreateObject("WScript.Shell");
                    var command = "";
                    for (var j = 0; j < deployCommands.length; j++) {
                        var deployCommand = deployCommands[j];
                        command = command + (command == "" ? "" : " ")
                                          + deployCommand.text.replace("$(package.directory)", project.output.directory)
                                                              .replace("$(package.name)", project.output.fileName)
                                                              .replace("$(package)", project.output.fullPath());
                    };
                    if (deployUpdate != null) {
                        var update = fileSystem.getFile(createUpdate(deployUpdate, project.output.fileName, project.revision));
                        command = command.replace("$(update.directory)", update.parentFolder + "\\")
                                         .replace("$(update.name)", update.name)
                                         .replace("$(update)", update.parentFolder + "\\" + update.name);
                    }
                    WScript.echo("Executing command...");
                    WScript.echo("\t" + command);
                    shell.run(command);
                    completed = true;
                    break;
                case "local":
                    if (deployLocation != null) {
                        if (fileSystem.FolderExists(deployLocation.text)) {
                            WScript.echo("Copying files...");
                            WScript.echo("\t msi to  - '" + deployLocation.text + "\\" + project.output.fileName + "'");
                            msi.copy(deployLocation.text + "\\" + project.output.fileName, true);
                            if (deployUpdate != null) {
                                var update = fileSystem.getFile(createUpdate(deployUpdate, project.output.fileName, project.revision));
                                WScript.echo("\t update specification to - '" + deployLocation.text + "\\update.xml'");
                                update.copy(deployLocation.text + "\\update.xml", true);
                            }
                            completed = true;
                        }
                        else {
                            WScript.echo("Unable to find deployment location folder '" + deployLocation.text + "' for " + project.configuration);
                        }
                    }
                    else {
                        WScript.echo("Deployment location not defined for " + project.configuration);
                    }
                    break;
                default:
                    WScript.echo("Deployment type '" + deployType + "' not supported");
            }
            if (completed)
                WScript.echo(project.configuration + " '" + deployType + "' Deployment to '" + deployTo + "' completed");
            else
                WScript.echo(project.configuration + " '" + deployType + "' Deployment to '" + deployTo + "' failed to complete");
        }
    }
    else {
        WScript.echo("No Deployment instructions found for " + project.configuration);
    }
}
else {
    WScript.echo("Output '" + project.output.fullPath() + "' not found.");
}

WScript.echo("\t");
WScript.echo("== Finished Deployment for " + project.configuration + " ==");
WScript.echo("\t");

var product = WScript.CreateObject("MSXML2.DOMDocument.6.0");
if (product.load("Product.wxs")) {
    product.setProperty("SelectionNamespaces", "xmlns:wix='http://schemas.microsoft.com/wix/2006/wi'");
    product.setProperty("SelectionLanguage", "XPath");
    WScript.echo("Reverting '" + "Product.wxs'...");

    var Directory = product.documentElement.selectSingleNode("//wix:Directory[@Id='']");
    var installDirectory = Directory.selectSingleNode("wix:Directory[1]");
    var removeComponent = product.documentElement.selectSingleNode("//wix:Component[@Id='RemoveFolders']");
    var removeFolder = product.documentElement.selectSingleNode("//wix:RemoveFolder[@Id='RemoveConfigurationFolder']");

    if (installDirectory) Directory.removeChild(installDirectory);
    if (removeFolder) removeComponent.removeChild(removeFolder);

    product.save("Product.wxs");
}
else {
    WScript.echo("Failed to Load 'Product.wxs'");
}

WScript.echo("\t");
WScript.echo("== Finished Post-Build script for Wix.Local ==");
