using System.Web;
using System.Web.Optimization;

namespace SDRDataParserGUI
{
    public class BundleConfig
    {
        // For more information on bundling, visit http://go.microsoft.com/fwlink/?LinkId=301862
        public static void RegisterBundles(BundleCollection bundles)
        {
            bundles.Add(new ScriptBundle("~/bundles/jquery").Include(
                        "~/Scripts/jquery-3.2.1.min.js"));

            // Use the development version of Modernizr to develop with and learn from. Then, when you're
            // ready for production, use the build tool at http://modernizr.com to pick only the tests you need.
            bundles.Add(new ScriptBundle("~/bundles/modernizr").Include(
                        "~/Scripts/modernizr-*"));

            bundles.Add(new ScriptBundle("~/bundles/bootstrap").Include(
                      "~/Scripts/bootstrap.min.js",
                      "~/Scripts/respond.js"));

            bundles.Add(new StyleBundle("~/Content/css").Include(
                      "~/Content/bootstrap.css",
                      "~/Content/font-awesome.css",
                      "~/Content/site.css",
                      "~/Content/explorer.css"));
            bundles.Add(new ScriptBundle("~/bundles/explorer").Include(
                        "~/Scripts/explorer.js"));
            bundles.Add(new ScriptBundle("~/bundles/SyntaxHighlighter/codeHighlight").Include(
                        "~/Scripts/SyntaxHighlighter/shCore.js",
                        "~/Scripts/SyntaxHighlighter/shBrushXml.js",
                        "~/Scripts/SyntaxHighlighter/shBrushJScript.js",
                        "~/Scripts/SyntaxHighlighter/shBrushCSharp.js"));
            bundles.Add(new StyleBundle("~/Content/jquery-ui/jquery-ui-css").Include(
                        "~/Content/jquery-ui/jquery-ui.css",
                        "~/Content/jquery-ui/jquery-ui.structure.css",
                        "~/Content/jquery-ui/jquery-ui.theme.css"
                        ));
            bundles.Add(new StyleBundle("~/bundles/quill").Include(
                        "~/Content/quill/quill.bubble.css",
                        "~/Content/quill/quill.core.css",
                        "~/Content/quill/quill.snow.css"
                        ));
            bundles.Add(new ScriptBundle("~/bundles/gridExports").Include(
                        "~/Scripts/jszip.min.js"
                        ));
        }
    }
}
