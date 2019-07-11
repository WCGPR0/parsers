using ExcelDna.Integration;
using System;
using System.Configuration;
using System.IO;
using System.Linq;
using System.Xml.Linq;

namespace ConstantContactSendTrades
{
    /** 
     * Globally shared static helper functions 
     */
    class GlobalFunctions
    {
        private const string errorLogFileName = "excelcampaignerrors.txt";
        private const string datetimeFormat = "yyyy-MM-dd HH:mm:ss.fff";
        public static string activeWBPath { get; private set; }

        public static void SetActiveWorkBookName()
        {
            var test = XlCall.Excel(XlCall.xlfCaller);
            var reference = (ExcelReference)XlCall.Excel(XlCall.xlfCaller);
            var sheetName = (string)XlCall.Excel(XlCall.xlSheetNm, reference);
            activeWBPath = Path.Combine((string)XlCall.Excel(XlCall.xlfGetDocument, 2, sheetName), (string)XlCall.Excel(XlCall.xlfGetDocument, 88, sheetName));
        }
        public static void SetActiveWorkBookName(string activeWBPath)
        {
            GlobalFunctions.activeWBPath = activeWBPath;
        }
        #region "Log"
        [System.Flags]
        private enum LogLevel
        {
            TRACE,
            DEBUG,
            INFO,
            WARNING,
            ERROR,
            FATAL
        }
        public static void TraceLog(string msg)
        {
            Log(msg, errorLogFileName, LogLevel.TRACE);
        }
        public static void DebugLog(string msg)
        {
            Log(msg, errorLogFileName, LogLevel.DEBUG);
        }
        public static void InfoLog(string msg)
        {
            Log(msg, errorLogFileName, LogLevel.INFO);
        }
        public static void WarnLog(string msg)
        {
            Log(msg, errorLogFileName, LogLevel.WARNING);
        }

        public static void ErrorLog(string msg)
        {
            Log(msg, errorLogFileName, LogLevel.ERROR);
        }
        private static void Log(string msg, string fileName, LogLevel level)
        {
            string filepath = "";
            string pretext;
            LogLevel? level_;
            try
            {
                level_ = (LogLevel)Enum.Parse(typeof(LogLevel), ConfigurationManager.AppSettings["Log.Level"], true);
            }
            catch
            {
                level_ = null;
            }
            if (level_ != null && level_ > level) return;
            switch (level)
            {
                case LogLevel.TRACE:
                    pretext = System.DateTime.Now.ToString(datetimeFormat) + " [TRACE]   ";
                    break;
                case LogLevel.INFO:
                    pretext = System.DateTime.Now.ToString(datetimeFormat) + " [INFO]    ";
                    break;
                case LogLevel.DEBUG:
                    pretext = System.DateTime.Now.ToString(datetimeFormat) + " [DEBUG]   ";
                    break;
                case LogLevel.WARNING:
                    pretext = System.DateTime.Now.ToString(datetimeFormat) + " [WARNING] ";
                    break;
                case LogLevel.ERROR:
                    pretext = System.DateTime.Now.ToString(datetimeFormat) + " [ERROR]   ";
                    break;
                case LogLevel.FATAL:
                    pretext = System.DateTime.Now.ToString(datetimeFormat) + " [FATAL]   ";
                    break;
                default:
                    pretext = "";
                    break;
            }
            if (!string.IsNullOrEmpty(activeWBPath))
            {
                filepath = (new FileInfo(activeWBPath)).Directory.FullName;
            }
            else
            {
                filepath = Vars.AssemblyDirectory;
            }

            filepath = Path.Combine(filepath, "ConstantContactAPISendTrades");
            CreateFolderSafe(filepath);
            filepath = Path.Combine(filepath, "logs");
            CreateFolderSafe(filepath);
            filepath = Path.Combine(filepath, Environment.UserName);
            CreateFolderSafe(filepath);


            filepath = Path.Combine(filepath, fileName);

            try
            {
                File.AppendAllLines(filepath, new[] { msg });
            }
            catch (System.UnauthorizedAccessException)
            {
                File.AppendAllLines(Path.Combine(Path.GetTempPath(), fileName), new[] { msg });
            }
        }

        public static void Log(XDocument doc, String fileName)
        {
            String filepath = "";
            if (!String.IsNullOrEmpty(activeWBPath))
            {
                filepath = (new FileInfo(activeWBPath)).Directory.FullName;
            }
            else
            {
                filepath = Vars.AssemblyDirectory;
            }

            filepath = Path.Combine(filepath, "ConstantContactAPISendTrades");
            CreateFolderSafe(filepath);
            filepath = Path.Combine(filepath, "logs");
            CreateFolderSafe(filepath);
            filepath = Path.Combine(filepath, Environment.UserName);
            CreateFolderSafe(filepath);


            filepath = Path.Combine(filepath, fileName);

            try
            {
                doc.Save(filepath);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                WarnLog(ex.Message);
            }
        }

        public static string getInstallPath()
        {
            return Path.GetDirectoryName(ExcelDnaUtil.XllPath);
        }

        public static string getEnvironmentPath()
        {
            return Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        }

        private static void CreateFolderSafe(string path)
        {
            if (!Directory.Exists(path))
            {
                Directory.CreateDirectory(path);
            }
        }
        #endregion

    }
}
