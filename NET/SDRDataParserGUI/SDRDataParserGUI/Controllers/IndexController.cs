using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using System.Web.Mvc;
using System.Xml;

namespace SDRDataParserGUI.Controllers
{
    [Authorize]
    public class IndexController : Controller
    {
        private static log4net.ILog Log { get; } = log4net.LogManager.GetLogger(typeof(IndexController));
        [UserAuthorizationFilter]
        public ActionResult Index()
        {
            string person_code = "";
            string person_org = "";
            string activeDir = (string)RouteData.Values["username"];
            if (Session["Role"] == null || String.Empty == (string)Session["Role"] ||
                Session["Email"] == null || String.Empty == (string)Session["Email"])
            {
                Session["Role"] = "";
                Session["Email"] = "";
                if (findPerson(activeDir, out person_code, out person_org))
                {
                    getPeopleDetails(person_code, person_org);
                }
            }
            return View();
        }

        //Profiles are set up through the BRT Reporting website
        private Boolean findPerson(string activeDir, out string personCode, out string personOrg)
        {
            personOrg = "";
            personCode = "";
            XmlDocument data_xml = new XmlDocument();
            string ret = Functions.MTRequest("AR.BR.PERSON.FIND", activeDir, true);
            if (ret != "")
            {
                data_xml.LoadXml(ret);
                XmlNode root = data_xml.SelectSingleNode("Person");
                if (root != null)
                {
                    personOrg = Functions.SelectSingleNodeSafe(root, "person_org");
                    personCode = Functions.SelectSingleNodeSafe(root, "person_code");
                    return true;
                }
            }
            return false;
        }

        /**
         * Gets the details for the person based on active directory.
         * WARNING; Possible Caution (since no PIN verification):
         * prone to active directory spoofing
         */
        private void getPeopleDetails(String userCode, String userOrg)
        {
            Log.Debug("A user visited the homepage; attempting to get info for: " + userCode + "@" + userOrg);
            XmlDocument data_xml = new XmlDocument();
            String ret = Functions.MTRequest("AR.SDR.PERSON.DETAILS.GET", userCode + "|" + userOrg, true);
            if (ret != "")
            {
                data_xml.LoadXml(ret);
                XmlNode root = data_xml.SelectSingleNode("DETAILS");
                String role = "";
                if (root != null)
                {
                    role = Functions.SelectSingleNodeSafe(root, "ROLE");
                }
                if (role == "")
                {
                    role = "BROKER";
                }
                Session["Role"] = role;
                /* EMAIL */
                Session["Email"] = Functions.SelectSingleNodeSafe(root, "EMAIL");
                /* LOCATION + TIMEZONE */
                string loc = Functions.SelectSingleNodeSafe(root, "LOCATION");
                Tuple<TimeZoneInfo, string> timeZone = null;
                if (loc.Equals("ST") | loc.Equals("NY") | loc.Equals("CT"))
                {
                    TimeZoneInfo tz = TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time");
                    timeZone = new Tuple<TimeZoneInfo, string>(tz, "");
                }
                else if (loc.Equals("LN"))
                {
                    TimeZoneInfo tz = TimeZoneInfo.FindSystemTimeZoneById("GMT Standard Time");
                    timeZone = new Tuple<TimeZoneInfo, string>(tz, "GMT");
                }
                else
                {
                    timeZone = new Tuple<TimeZoneInfo, string>(TimeZoneInfo.Utc, "UTC");
                }
                Session["timeZone"] = timeZone;
            }
        }
    }
}