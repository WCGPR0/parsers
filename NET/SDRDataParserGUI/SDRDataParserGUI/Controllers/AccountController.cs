using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Web;
using System.Web.Mvc;
using System.Xml;

namespace SDRDataParserGUI.Controllers
{
    [Authorize]
    public class AccountController : Controller
    {
        private static log4net.ILog Log { get;} = log4net.LogManager.GetLogger(typeof(AccountController));

        [HttpGet]
        [UserAuthorizationFilter]
        [AdminAuthorizationFilter]
        public ActionResult Index()
        {
            HashSet<Tuple <string, string> > products = new HashSet<Tuple <string, string > >(); //< The main products that is used to select and filter the data
            foreach (SDRDataParserGUI.Models.GraphData.Transaction transaction in DataObtainer.data.reportList)
            {
                Tuple<string, string> product = new Tuple<string, string>(transaction.contract_name, transaction.source);
                products.Add(product);
            }

                var list = new List<string>();
                XmlDocument data_xml = new XmlDocument();
            if (RouteData.Values["username"] != null)
            {
                String ret = Functions.MTRequest("AR.WEB.GROUP.GET", (string)RouteData.Values["username"], false);
                if (ret != "")
                {
                    data_xml.LoadXml(ret);
                    XmlNode root = data_xml.SelectSingleNode("report");
                    if (root != null)
                    {
                        foreach (XmlNode groupname in root.SelectNodes("group"))
                        {
                            list.Add(Functions.SelectSingleNodeSafe(groupname, "groupname"));
                        }
                    }
                }
            }

                ViewBag.members = list;

                return View(products);
            }          

        [HttpPost]
        [UserAuthorizationFilter]
        [AdminAuthorizationFilter]
        public ActionResult Index(FormCollection collection)
        {
            var groupName = collection["Group"];

            if (collection["submit"] != null)
            {
                var filters = collection["Filter"].Split(',') ?? null;
                String ret = Functions.MTRequest("AR.WEB.GROUP.CLEAR", groupName, true);
                XmlDocument root = new XmlDocument();
                root.LoadXml(ret);
                root.SelectSingleNode("groupID");
                if (root != null)
                {
                    Log.Debug("Updating group: " + groupName);
                    int groupID = Int32.Parse(Functions.SelectSingleNodeSafe(root, "groupID"));
                    foreach (string s in filters)
                    {
                        Functions.MTRequest("AR.WEB.GROUP.UPDATE", groupID + "|" + s.Split('|')[0] + "|" + s.Split('|')[1] + "|" + 51, true);
                    }
                    Session["Group"] = groupID;
                }
            }
            else if (collection["delete"] != null)
            {
                Log.Debug("Deleting group: " + groupName);
                Functions.MTRequest("AR.WEB.GROUP.DELETE", groupName, true);
            }
            
            return RedirectToAction("Index", "Index");
        }


        [HttpGet]
        [UserAuthorizationFilter]
        public ActionResult Group()
        {
            var list = new List<string>();
            XmlDocument data_xml = new XmlDocument();
            string ret = Functions.MTRequest("AR.WEB.GROUP.GET", (string)RouteData.Values["username"], true);
            if (ret != "")
            {
                data_xml.LoadXml(ret);
                XmlNode root = data_xml.SelectSingleNode("report");
                if (root != null)
                {
                    foreach (XmlNode groupname in root.SelectNodes("group"))
                    {
                        list.Add(Functions.SelectSingleNodeSafe(groupname, "groupname"));
                    }
                }
            }
            ViewBag.members = list;
            return View();
        }

        [HttpPost]
        [UserAuthorizationFilter]
        public ActionResult Group(FormCollection collection)
        {
            XmlDocument data_xml = new XmlDocument();
            String ret = Functions.MTRequest("AR.WEB.GROUP.SYMBOLS.GET", collection["Group"], true);
            if (ret != "")
            {
                data_xml.LoadXml(ret);
                XmlNode group = data_xml.SelectSingleNode("report/group");
                if (group != null)
                {
                        String filter = Functions.SelectSingleNodeSafe(group, "groupfilter");
                        Session["filter"] = filter.Split(',');
                }
            }
            return RedirectToAction("Index", "Home");
        }



        [HttpGet]
        [UserAuthorizationFilter]
        [AdminAuthorizationFilter]
        public ActionResult Members()
        {
            var list = new List<string>();
            XmlDocument data_xml = new XmlDocument();
            String ret = Functions.MTRequest("AR.WEB.GROUP.GET", "ALL", true);
            if (ret != "")
            {
                data_xml.LoadXml(ret);
                XmlNode root = data_xml.SelectSingleNode("report");
                if (root != null)
                {
                    foreach (XmlNode groupname in root.SelectNodes("group"))
                    {
                        list.Add(Functions.SelectSingleNodeSafe(groupname, "groupname"));
                    }
                }
            }

            List<string> people = new List<string>();
            string file_ = Server.MapPath(@"~/App_Data/brokerMonthlyHistorical.xml"); //< This file is generated & updated by BRTCreateCache (BrokerReporting)
            XmlDocument xmlDocMonthlyRev = new XmlDocument();
            xmlDocMonthlyRev.LoadXml(Functions.DecryptString(System.IO.File.ReadAllText(file_)));
            XmlNodeList brokers = xmlDocMonthlyRev.SelectNodes("Document/broker");
            foreach (XmlNode brokerNode in brokers)
            {
                String brokerName = brokerNode.Attributes["name"].Value;
                if (brokerName.Length != 0)
                    people.Add(brokerName);
            }

            ViewBag.members = list;
            ViewBag.people = people;
            return View();
        }

        [HttpPost]
        [UserAuthorizationFilter]
        [AdminAuthorizationFilter]
        public ActionResult Members(FormCollection collection)
        {
            XmlDocument data_xml = new XmlDocument();
            string ret = Functions.MTRequest("AR.WEB.PEOPLE.CLEAR", collection["Group"], true);
            string[] members = collection["Members"].Split(',') ?? null;
            foreach (string member in members) {
                string ret_ = Functions.MTRequest("AR.WEB.PEOPLE.UPDATE", collection["Group"] + '|' + member, true);
            }
            return RedirectToAction("Index", "Index");
        }

    }
  }