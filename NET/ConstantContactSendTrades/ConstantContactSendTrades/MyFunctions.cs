using ConstantContactSendTrades.API.GoogleDrive;
using ExcelDna.Integration;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace ConstantContactSendTrades
{
    /**
     * UDFs- User defined functions
     */
    public static class MyFunctions
    {
        [ExcelFunction(Description = "Send Excel Workbook as a campaign with specific workbook", IsMacroType =true)]
        public static int SendWorkBook([ExcelArgument("Workbook Path")]string myWBPath, [ExcelArgument("URL parameter")]string url)
        {
            int status = 0;
            try
            {
                GlobalFunctions.SetActiveWorkBookName(myWBPath);
                ConstantContactAPI constantContactAPI = new ConstantContactAPI();
                GlobalFunctions.DebugLog("ConstantContact API initialized");

                Dictionary<string, string> parameters = new Dictionary<string, string>();
                parameters.Add("DateTime", DateTime.Now.ToString("yyyy-MM-dd"));
                parameters.Add("URL", url);
                parameters.Add("FileName", (new FileInfo(GlobalFunctions.activeWBPath)).Name);
                status = constantContactAPI.SendEmails(parameters);
                GlobalFunctions.DebugLog($"Email campaigns sent: {(new FileInfo(GlobalFunctions.activeWBPath)).Name}");
                GlobalFunctions.InfoLog("Finished SendWorkBook UDF...");

            }
            catch (Exception ex)
            {
                GlobalFunctions.WarnLog("Encountered error running SendWorkBook UDF...:" + ex.Message);
                GlobalFunctions.TraceLog(ex.ToString());
                status = -1;
            }
            return status;
        }

        [ExcelFunction(Description = "Upload Excel Workbook to Google Drive with specific workbook", IsMacroType =true)]
        public static string UploadWorkBook2([ExcelArgument("Workbook Path")]string myWBPath)
        {
            string url = String.Empty;
            try
            {
                GlobalFunctions.SetActiveWorkBookName(myWBPath);
                GoogleDriveAPI gdrive = new GoogleDriveAPI();
                GlobalFunctions.DebugLog($"GoogleDrive API initialized. Uploading file: {GlobalFunctions.activeWBPath}");
                url = gdrive.upload(GlobalFunctions.activeWBPath);
                GlobalFunctions.DebugLog($"File successfully uploaded; public url: {url}");
                GlobalFunctions.InfoLog("Finished UploadWorkBook UDF...");
            }
            catch (Exception ex)
            {
                GlobalFunctions.WarnLog("Encountered error running UploadWorkBook UDF:" + ex.Message);
                GlobalFunctions.TraceLog(ex.ToString());
            }
            return url;

        }
    }
}
