using System;
using System.Collections.Generic;
using System.Linq;
using System.Web.Mvc;
using C1.Web.Mvc;
using SDRDataParserGUI.Models;
using System.Collections;
using System.Globalization;
using System.Net.Mail;
using System.Web;
using System.Web.Routing;

namespace SDRDataParserGUI.Controllers
{
    [Authorize]
    public class HomeController : Controller
    {

        private static log4net.ILog Log { get; } = log4net.LogManager.GetLogger(typeof(HomeController));
        private readonly ControlOptions _gridDataModel = new ControlOptions
        {
            Options = new OptionDictionary
            {
                {"Items",new OptionItem{Values = new List<string> {"5", "50", "500", "5000", "50000", "100000", "500000", "1000000"},CurrentValue = "100000"}},
                {"Column Resize", new OptionItem {Values = new List<string> {"100", "150"}, CurrentValue = "100"}},
                {"Export Data", new OptionItem {Values = new List<string> {"Excel", "PDF"}, CurrentValue = "Excel"}}
            }
        };
        private Dictionary<string, FilterType> GetFilterTypes(ControlOptions controlOptions)
        {
            var filterTypes = new Dictionary<string, FilterType>();
            foreach (var item in controlOptions.Options)
            {
                filterTypes.Add(item.Key, (FilterType)Enum.Parse(typeof(FilterType), item.Value.CurrentValue));
            }
            return filterTypes;
        }
        private static OptionItem CreateOptionItem()
        {
            return new OptionItem { Values = new List<string> { "None", "Condition", "Value", "Both" }, CurrentValue = "Both" };
        }

        private readonly ControlOptions _gridFilterModel = new ControlOptions
        {
            Options = new OptionDictionary
            {
                {"contract_name", CreateOptionItem()},
                {"source", CreateOptionItem()},
                {"strategy", CreateOptionItem()},
                {"start_date", CreateOptionItem()},
                {"end_date", CreateOptionItem()}
              //  {"interest", CreateOptionItem()},
            //    {"change", CreateOptionItem()}
            }
        };

        [HttpPost]
        [UserAuthorizationFilter]
        public ActionResult Email(FormCollection collection)
        {
            String emailAddr = (string)Session["email"];
            if (emailAddr == null || String.Empty == emailAddr)
            {
                return Json(new { success = false, responseText = "Email Address is empty or null. Verify cookies." });
                Log.Error("Invalid email when sending alert for user: " + (string)RouteData.Values["username"]);
            }

            try
            {
                string server = Functions.GetConfigSetting("email");

                MailMessage emailMessage = new MailMessage();
                emailMessage.From = new MailAddress("");
                emailMessage.Subject = "SDRDataParser Alert: " + Functions.getTime(Session["timeZone"], "hh:mm:sstt", true) + Functions.getTime(Session["timeZone"], " on MMMM dd, yyyy", false);
                emailMessage.To.Add(emailAddr);
                emailMessage.IsBodyHtml = true;

                SmtpClient emailClient = new SmtpClient(server);

                GraphData.Transaction transaction = new GraphData.Transaction(collection);

                string htmlString = RenderViewToString("Home", "EmailAlert", transaction, new ControllerContext(this.Request.RequestContext, new HomeController()));

                emailMessage.Body += htmlString;

                emailClient.Send(emailMessage);

                return Json(new { success = true, responseText = "Successfully sent email alert" });
            }
            catch (Exception e)
            {
                Log.Error("An error occured while sending email: " + e.Message);
                return Json(new { success = false, responseText = "Error sending email" + e.Message});
            }
        }

        public static string RenderViewToString(string controllerName, string viewName, object viewData, ControllerContext controllerContext)
        {
            using (var writer = new System.IO.StringWriter())
            {
                var razorViewEngine = new RazorViewEngine();
                var razorViewResult = razorViewEngine.FindView(controllerContext, viewName, "", false);

                var viewContext = new ViewContext(controllerContext, razorViewResult.View, new ViewDataDictionary(viewData), new TempDataDictionary(), writer);
                razorViewResult.View.Render(viewContext, writer);
                return writer.ToString();

            }
        }

        [UserAuthorizationFilter]
        public ActionResult Index(FormCollection collection)
        {

            //Update the data if it hasn't been updated in a while
            DataObtainer.updated();

            IValueProvider data = collection;
          
            if (CallbackManager.CurrentIsCallback)
            {
                var request = CallbackManager.GetCurrentCallbackData<CollectionViewRequest<object>>();
                if (request != null && request.ExtraRequestData != null)
                {
                    var extraData = request.ExtraRequestData.Cast<DictionaryEntry>()
                        .ToDictionary(kvp => (string)kvp.Key, kvp => kvp.Value.ToString());
                    data = new DictionaryValueProvider<string>(extraData, CultureInfo.CurrentCulture);
                }
            }

            _gridDataModel.LoadPostData(data);
            List<GraphData.Transaction> reportList_ = new List<GraphData.Transaction>(DataObtainer.data.reportList);
            string[] filter = (string[])Session["Filter"]; //Filters from user selection
            if (filter != null) reportList_ = reportList_.Where(x => filter.Contains(x.contract_name)).ToList();
            reportList_ = reportList_.GetRange(0, Math.Min(reportList_.Count, Convert.ToInt32(_gridDataModel.Options["items"].CurrentValue)));
            ViewBag.DemoOptions = _gridDataModel;
            ViewBag.FilterTypes = GetFilterTypes(_gridFilterModel);
            return View("_DataTable", reportList_);
        }
    }
}
