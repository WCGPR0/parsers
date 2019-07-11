using CTCT;
using CTCT.Components.Contacts;
using CTCT.Components.EmailCampaigns;
using CTCT.Exceptions;
using CTCT.Services;
using System;
using System.Collections.Generic;
using System.Configuration;
using System.Linq;
using System.Text;
using System.Xml.Linq;
using System.Xml.Xsl;

namespace ConstantContactSendTrades
{
    public class ConstantContactAPI
    {
        private static string StylesheetLocationRoot = @"http://tfs/energy/transforms";
        private string _apiKey = string.Empty;
        private string _accessToken = string.Empty;
        private string _emailTemplateTransformUrl;
        private string _emailConnectionTransformUrl;
        private IEmailCampaignService _emailCampaignService = null;
        private ICampaignScheduleService _emailCampaginScheduleService = null;
        private IListService _listService = null;

        public ConstantContactAPI()
        {
            _apiKey = ConfigurationManager.AppSettings["CC.APIKey"];
            _accessToken = ConfigurationManager.AppSettings["CC.accessToken"];
            _emailTemplateTransformUrl = StylesheetLocationRoot + "/" + ConfigurationManager.AppSettings["campaignTemplate"];
            Initialize();
        }
        private void Initialize()
        {
            try
            {
                string state = "ok";
                if (String.IsNullOrEmpty(_emailTemplateTransformUrl))
                    throw new Exception("Missing email campaign template location from configuration");

                if ((String.IsNullOrEmpty(_apiKey) || String.IsNullOrEmpty(_accessToken)) && !String.IsNullOrEmpty(_emailConnectionTransformUrl))
                {
                    XDocument emailConnection = new XDocument();
                    using (System.Xml.XmlWriter writer = emailConnection.CreateWriter())
                    {
                        XslCompiledTransform emailConnectionTransform = new XslCompiledTransform();
                        emailConnectionTransform.Load(_emailConnectionTransformUrl);
                        emailConnectionTransform.Transform(XDocument.Parse("<dummy />").CreateReader(), writer);
                    }
                }

                if (String.IsNullOrEmpty(_accessToken))
                {
                    _accessToken = OAuth.AuthenticateFromWinProgram(ref state);
                }
                if (string.IsNullOrEmpty(_accessToken))
                {
                    throw new Exception("Unable to authenticate & retrieve access token");
                }

                //initialize ConstantContact members
                IUserServiceContext userServiceContext = new UserServiceContext(_accessToken, _apiKey);
                ConstantContactFactory _constantContactFactory = new ConstantContactFactory(userServiceContext);
                _emailCampaignService = _constantContactFactory.CreateEmailCampaignService();
                _emailCampaginScheduleService = _constantContactFactory.CreateCampaignScheduleService();
                _listService = _constantContactFactory.CreateListService();
            }
            catch (OAuth2Exception oauthEx)
            {
                GlobalFunctions.WarnLog(string.Format("Authentication failure: {0}", oauthEx.Message));
            }
        }
        private XDocument createEmailConnectionXML()
        {
            XDocument doc = new XDocument(
                    new XElement("EmailConnection",
                        /* <!-- <Create Contacts> --> */
                        new XElement("ContactList",
                            _listService.GetLists(null).Select(item => new XElement("contact", new XAttribute("name", item.Name), item.Id.ToString()))
                        )
                /* <!-- </Create Contacts> --> */
                )
             );
#if DEBUG
            GlobalFunctions.Log(doc, "EmailConnection.xml");
#endif
            return doc;
        }

        /** Creates a list of campaigns, and sends it, with content based on the campaignTemplate transform */
        public int SendEmails(Dictionary<string, string> parameters)
        {
            int status = 0;
            try
            {
                IEnumerable<EmailCampaign> campaigns = CreateCampaigns(parameters);
                GlobalFunctions.InfoLog($"{0} campaigns has been created. Scheduling campaigns.");
                if (campaigns.Count() == 0)
                    status = -1;
                foreach (EmailCampaign campaign in campaigns)
                {
                    EmailCampaign campaign_ = _emailCampaignService.AddCampaign(campaign);
                    Schedule schedule;
                    schedule = new Schedule() { ScheduledDate = DateTime.Now.AddMinutes(20).ToUniversalTime() };
                    schedule = _emailCampaginScheduleService.AddSchedule(campaign_.Id, schedule);
                    if (schedule == null)
                    {
                        GlobalFunctions.WarnLog("Campaign was saved, but failed to schedule it!");
                        status = -1;
                    }
                }
            }
            catch (IllegalArgumentException illegalEx)
            {
                GlobalFunctions.ErrorLog(GetExceptionsDetails(illegalEx, "IllegalArgumentException"));
                status = -1;
            }
            catch (CtctException ctcEx)
            {
                GlobalFunctions.ErrorLog(GetExceptionsDetails(ctcEx, "CtctException"));
                status = -1;
            }
            catch (OAuth2Exception oauthEx)
            {
                GlobalFunctions.ErrorLog(GetExceptionsDetails(oauthEx, "OAuth2Exception"));
                status = -1;
            }
            catch (Exception ex)
            {
                GlobalFunctions.ErrorLog(GetExceptionsDetails(ex, "Exception"));
                status = -1;
            }
            return status;
        }
        private string GetExceptionsDetails(Exception ex, string exceptionType)
        {
            StringBuilder sbExceptions = new StringBuilder();

            sbExceptions.Append(string.Format("{0} thrown:\n", exceptionType));
            sbExceptions.Append(string.Format("Error message: {0}", ex.Message));

            return sbExceptions.ToString();
        }

        private IEnumerable<EmailCampaign> CreateCampaigns(Dictionary<string, string> parameters)
        {
            XDocument emailTemplate = new XDocument();
            using (System.Xml.XmlWriter writer = emailTemplate.CreateWriter())
            {
                XslCompiledTransform emailTemplateTransform = new XslCompiledTransform();
                XsltArgumentList emailTemplateArgList = new XsltArgumentList();
                foreach (KeyValuePair<string, string> parameter in parameters)
                {
                    emailTemplateArgList.AddParam(parameter.Key, "", parameter.Value);
                }
                emailTemplateTransform.Load(_emailTemplateTransformUrl);
                emailTemplateTransform.Transform(createEmailConnectionXML().CreateReader(), emailTemplateArgList, writer);
            }
            List<EmailCampaign> campaigns = new List<EmailCampaign>();
            foreach (XElement campaignElement in emailTemplate.Descendants("campaign"))
            {
                EmailCampaign campaign = new EmailCampaign();
                campaign.EmailContentFormat = CampaignEmailFormat.XHTML;
                campaign.Status = CampaignStatus.SENT;
                foreach (XElement property in campaignElement.Elements("property"))
                {
                    string name = property.Attribute("name").Value;
                    string value = String.Concat(property.Nodes());
                    try
                    {
                        campaign.GetType().GetProperty(name).SetValue(campaign, value, null);
                    }
                    catch (Exception)
                    {
                        throw new Exception($"Error with campaign property setting, {name}, with value, {value}");
                    }
                }
                MessageFooter msgFooter = new MessageFooter();
                campaign.MessageFooter = msgFooter;
                foreach (XElement method in emailTemplate.Descendants("footer").Elements("property"))
                {
                    string name = method.Attribute("name").Value;
                    string value = method.Value;
                    try
                    {
                        msgFooter.GetType().GetProperty(name).SetValue(msgFooter, value, null);
                    }
                    catch (Exception)
                    {
                        throw new Exception($"Error with footer property setting, {name}, with value, {value}");
                    }
                }
                List<SentContactList> lists = new List<SentContactList>();
                foreach (XElement method in emailTemplate.Descendants("contacts").Elements("contact"))
                {
                    string id = method.Attribute("id").Value;
                    SentContactList contactList = new SentContactList()
                    {
                        Id = id
                    };
                    lists.Add(contactList);
                }
                campaign.Lists = lists;
                campaigns.Add(campaign);
            }
            return campaigns;

        }
    }
}
