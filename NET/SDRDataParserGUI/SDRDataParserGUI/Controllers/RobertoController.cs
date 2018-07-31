using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Web;
using System.Web.Mvc;

namespace SDRDataParserGUI.Controllers
{
    public class RobertoController : Controller
    {
        private static log4net.ILog Log { get; } = log4net.LogManager.GetLogger(typeof(MarketController));

        [HttpPost]
        public string Home(FormCollection collection)
        {
            const string openingTag = "QQZPQZOT7JFUVFLBAO4OL6O565V4Y4FIWMQM22T2IYGF5FAQV5G3AFMTFSGJ4GSPPZPOX7DFOKJLD3BCQO5AMKJQRYBBEZI5CZHD5PP3DTUY4OXO7XPA6TVSLGNCLBCNL77ULB2UYSWXAAZCD7XRONRRHAN5BUN32N3QN3Q6XCHV6ZK67MLQ8888";
            const string closingTag = "EWJZ76ZXGE6NH7NGBQMWSEN4WLMGIHQXKT3WERSW2FHLJKTZXJEJCLVA4TYSMGNGTN2W6FWBTTW4R7ZKSRR62THBEA2EEI6NME7IJZ74SJ3N3BS4D7IYHOGZ6JGEV56PTYSN2XJ2XDJ6UTWE27ZZU5QNNZKMPICOIM6TI6FGKQ3AVVTCGN7VP23R2EDYQ6OOUMUMPH3JLLSDPOFJLXHDGUST3ZRMVNGFYIMYJMCZVS5DNXJH2DOB42DJ72J7FSWCGVVHL5XP3W7RTGQ5DZPEZX4FQNLA4FZOOQ2S5Y4T7HJ5WUDNPG2WWEIU5N2MAGF3MHBNPLWP4NCGVM27EYIXHTJQYZXMLP6UEC7HJDKJGLH46UGJBSYALA5KXWDUHY6VDDNGFTGENWQ4QGQTZV3HMWN6VTAICPJIIUDBOOL6AZB7TR2FTH4UFF7H7W4UUUX64C2C4CP5XA7BUM3Y4DF42BDD24NQRSSQNY2DV3RO5QR7PYCYUXPMTKFJF6POBHSPGDHY66TL45D2ESOAUQP35ZDCMI3VWKCFIEHOM5RNHQBQ6VGRPGSXFHCDI5LEI2LD2KYBYBPVNPL2W888";
            string fileName = DateTime.Now.ToString("yyyyMMddHHmmssfff");
            FileInfo file = new FileInfo(Server.MapPath(string.Format(@"~/media/file/Roberto/{0}.txt", fileName)));
            string data_ = Functions.DecryptString(openingTag, typeof(Int32)) + collection["data"] + Functions.DecryptString(closingTag, typeof(Int32));
            data_ = System.Text.RegularExpressions.Regex.Replace(data_, "\r\n?|\n", "");
            System.IO.File.WriteAllText(file.FullName, data_);
            Log.Debug("New Roberto file updated: " + file);
            return Url.Action("Index", "Roberto", new { id = Functions.EncryptString(fileName) });
        }

        [AllowAnonymous]
        public ActionResult Index(string id)
        {
            string file_;
            if (id == null)
            {
                return new HttpNotFoundResult();
            }
            else
            {
                string id_ = Functions.DecryptString(id, typeof(Int32));

                file_ = Server.MapPath(string.Format(@"~/media/file/Roberto/{0}.txt", id_));
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
                    Log.Debug("Invalid attempt to request file: " + id);
                    return View("Error", null);
                }
            }
        }
    }
}