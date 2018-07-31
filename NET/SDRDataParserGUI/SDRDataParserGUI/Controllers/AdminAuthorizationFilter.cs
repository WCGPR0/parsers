using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using System.Web.Mvc;

namespace SDRDataParserGUI
{
    public class AdminAuthorizationFilter : ActionFilterAttribute
    {
        private static log4net.ILog Log { get; } = log4net.LogManager.GetLogger(typeof(AdminAuthorizationFilter));
        public override void OnActionExecuting(ActionExecutingContext filterContext)
        {
            if (!filterContext.HttpContext.User.Identity.IsAuthenticated /* Not connected through LAN (anonymous login) */ || string.IsNullOrEmpty((string)filterContext.HttpContext.Session["Role"]) || !filterContext.HttpContext.Session["Role"].Equals("DEVELOPER"))
            {
                Log.Debug("Invalid credentials; non-Admin accessed Admin page: " + filterContext.HttpContext.User.Identity.Name);
                filterContext.Result = new ViewResult { ViewName = "UnAuthorized" };
            }
        }
    }
}