using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using System.Web.Mvc;

namespace SDRDataParserGUI
{
    public class MarketAdminAuthorizationFilter : ActionFilterAttribute
    {
        private static log4net.ILog Log { get; } = log4net.LogManager.GetLogger(typeof(AdminAuthorizationFilter));
        public override void OnActionExecuting(ActionExecutingContext filterContext)
        {
            List<string> admins = Functions.GetConfigSetting("Admins.Market").Split(',').ToList();
            if (!filterContext.HttpContext.User.Identity.IsAuthenticated && (filterContext.RouteData.Values["username"] != null) && (admins.Contains(filterContext.RouteData.Values["username"].ToString().ToLower())))
            {
                Log.Debug("Invalid credentials; non-Admin accessed Admin page: " + filterContext.HttpContext.User.Identity.Name);
                filterContext.Result = new ViewResult { ViewName = "UnAuthorized" };
            }
        }
    }
}