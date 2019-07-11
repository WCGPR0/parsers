var project =
{
    directory: WScript.Arguments(0),
    configuration: WScript.Arguments(1),
    installType: WScript.Arguments(2)
}

var fileSystem = WScript.CreateObject("Scripting.FileSystemObject");

WScript.echo("== Starting Pre-Build script for .Windows.Excel.Setup ==");
WScript.echo("\t");

var product = WScript.CreateObject("MSXML2.DOMDocument.6.0");
if (product.load("Product.wxs")) {
    product.setProperty("SelectionNamespaces", "xmlns:wix='http://schemas.microsoft.com/wix/2006/wi'");
    product.setProperty("SelectionLanguage", "XPath");

    var Directory = product.documentElement.selectSingleNode("//wix:Directory[@Id='']");
    var removeComponent = product.documentElement.selectSingleNode("//wix:Component[@Id='RemoveFolders']");
    //<Directory Id="INSTALLFOLDER" Name="$(var.OutputDirectoryName)" ComponentGuidGenerationSeed="$(var.GuidGenerationSeed)"/>
    var installDirectory = product.createNode(1, "Directory", "http://schemas.microsoft.com/wix/2006/wi");
    installDirectory.setAttribute("Id", "INSTALLFOLDER");
    installDirectory.setAttribute("Name", "$(var.OutputDirectoryName)");
    installDirectory.setAttribute("ComponentGuidGenerationSeed", "$(var.GuidGenerationSeed)");
    //<Directory Id="Configuration" Name="$(var.Configuration)"/>
    var configDirectory = product.createNode(1, "Directory", "http://schemas.microsoft.com/wix/2006/wi");
    configDirectory.setAttribute("Id", "Configuration");
    configDirectory.setAttribute("Name", "$(var.Configuration)");
    //<RemoveFolder Id="RemoveConfigurationFolder" Directory="Configuration" On="uninstall"/>
    var removeFolder = product.createNode(1, "RemoveFolder", "http://schemas.microsoft.com/wix/2006/wi")
    removeFolder.setAttribute("Id", "RemoveConfigurationFolder");
    removeFolder.setAttribute("Directory", "Configuration");
    removeFolder.setAttribute("On", "uninstall");
        if (project.installType == "Specific") {
            WScript.echo("Setting Install Directory to '{ProgramFilesFolder}\\ConstantContactSendTrades\\" + project.configuration + "'");
            configDirectory.appendChild(installDirectory);
            Directory.appendChild(configDirectory);
            removeComponent.appendChild(removeFolder);
        }
        else {

            WScript.echo("Setting Install Directory to '{ProgramFilesFolder}\\ConstantContactSendTrades");
            Directory.appendChild(installDirectory);
        }
        product.save("Product.wxs");
    }
else {
    WScript.echo("Failed to Load 'Product.wxs'");
}

WScript.echo("\t");
WScript.echo("== Finished Pre-Build script for Wix.Local ==");
