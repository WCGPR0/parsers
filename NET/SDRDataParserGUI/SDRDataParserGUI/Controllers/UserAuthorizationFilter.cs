using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using System.Web.Mvc;

namespace SDRDataParserGUI
{
    public class UserAuthorizationFilter : ActionFilterAttribute
    {
        private static log4net.ILog Log { get; } = log4net.LogManager.GetLogger(typeof(UserAuthorizationFilter));
        public override void OnActionExecuting(ActionExecutingContext filterContext)
        {
            string activeDir = filterContext.HttpContext.User.Identity.Name;
            if (activeDir.Length > 10 && activeDir.Substring(0, 9).ToUpper().Equals(""))
                activeDir = activeDir.Substring(10);
            else if (activeDir.Length > 9 && activeDir.Substring(0, 8).ToUpper().Equals(""))
                activeDir = activeDir.Substring(9);
            else activeDir = "";

            if (activeDir == "")
            {
                Log.Debug("An user outside the network has accessed the site: " + filterContext.HttpContext.User.Identity.Name);
                filterContext.Result = new ViewResult { ViewName = "UnAuthorized" };
            }
            filterContext.RouteData.Values.Add("username", activeDir);
        }
    }
}