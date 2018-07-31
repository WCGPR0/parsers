using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Web;
using System.Web.Mvc;

namespace SDRDataParserGUI.Controllers
{
    [Authorize]
    public class MarketController : Controller
    {
        private static log4net.ILog Log { get; } = log4net.LogManager.GetLogger(typeof(MarketController));
        [UserAuthorizationFilter]
        [MarketAdminAuthorizationFilter]
        public ActionResult Home()
        {
            return View();
        }

        [UserAuthorizationFilter]
        public ActionResult Index(int? id)
        {
            string file_;
            if (id == null) file_ = Server.MapPath(string.Format(@"~/App_Data/Market/{0}.txt", DateTime.Now.ToString("yyyyMMdd")));
            else file_ = Server.MapPath(string.Format(@"~/App_Data/Market/{0}.txt", id));
            //string file_ = Directory.GetFiles(Server.MapPath(@"~/App_Data/Market")).OrderBy(path => Int32.Parse(Path.GetFileNameWithoutExtension(path))).Last();
            if (System.IO.File.Exists(file_))
                {
                    using (StreamReader file = new StreamReader(file_))
                    {
                        ViewBag.Header = System.IO.File.GetLastWriteTime(file_).ToString();
                        return View(model: file.ReadToEnd());
                    }
                }
                else
                {
                    IEnumerable<string> files = Directory.GetFiles(Server.MapPath(@"~/App_Data/Market"), "*.txt").Select(Path.GetFileName).Select(Path.GetFileNameWithoutExtension);
                    ViewBag.Title = "In Progress";
                    return View("Error", files);
                }
        }

        [UserAuthorizationFilter]
        [MarketAdminAuthorizationFilter]
        [ValidateInput(false)]
        public JavaScriptResult Update(string data)
        {
            FileInfo file = new FileInfo(Server.MapPath(string.Format(@"~/App_Data/Market/{0}.txt", DateTime.Now.ToString("yyyyMMdd"))));
            System.IO.File.WriteAllText(file.FullName, data);
            Log.Debug("New market file updated: " + file);
            return JavaScript("window.location = '" + Url.Action("Index", "Market") + "'");
        }
    }
}