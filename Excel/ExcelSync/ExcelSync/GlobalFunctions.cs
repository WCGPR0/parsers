using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Data.SqlClient;
using System.Data;
using System.IO;
using System.Collections.Concurrent;
using Excel = Microsoft.Office.Interop.Excel;
using Office = Microsoft.Office.Core;
using System.Runtime.InteropServices;
using System.Threading;

namespace ExcelSync
{
    class GlobalFunctions
    {
        private static SqlConnection dConn;

        private const Boolean EnableInfoLogging = false;
        private const String infoLogFileName = "excelshareerrors.txt";
        private const String errorLogFileName = "excelshareerrors.txt";
 
        public static Dictionary<String, WorksheetExt> worksheetBounds = new Dictionary<string, WorksheetExt>();

        
        public static string _activeWBName;
        public static string _activeWBExt;
        public static string ActiveWBName
        {
            get { return _activeWBName; }
        }
        public static string ActiveWBExt
        {
            get { return _activeWBExt; }
        }
        public static String ActiveWBPath;
        public static String ActiveWBFullPath;

        public static Excel.Range createRange(Excel.Worksheet ws, Int32 row, Int32 col)
        {
            if (row > 0 && col > 0)
            {
                return (Excel.Range)ws.Cells[row, col];
            }
            return null;
        }
        public static string GetExcelColumnName(int columnNumber)
        {
            int dividend = columnNumber;
            string columnName = String.Empty;
            int modulo;

            while (dividend > 0)
            {
                modulo = (dividend - 1) % 26;
                columnName = Convert.ToChar(65 + modulo) + columnName;
                dividend = ((dividend - modulo) / 26);
            }

            return columnName;
        }
        public static void ClearCell(CellUpdate uc, Excel.Worksheet ws)
        {
      
            Excel.Range thisRange = null;

            try
            {
                WaitForApplicationReady();
                
                thisRange = createRange(ws, uc.Row, uc.Col);
                if (thisRange != null)
                {
                        WaitForApplicationReady();
                        thisRange.ClearComments();


                        //dynamic oldFC = null;
                        //dynamic oldAppliesTo = null;
                        //if (thisRange.FormatConditions.Count > 0)
                        //{
                        //    foreach (dynamic fc in thisRange.FormatConditions)
                        //    {
                        //        if (fc.Type != 4)
                        //        {
                        //            oldFC = fc;
                        //            oldAppliesTo = fc.AppliesTo;
                        //        }
                        //    }


                        //    thisRange.FormatConditions.Delete();
                        //    oldFC.ModifyAppliesToRange(oldAppliesTo);
                        //}
                       
                }
            }
            catch (Exception ex)
            {
                ErrorLog(ex, uc);
                throw;
            }
            finally
            {
                if (thisRange != null) Marshal.ReleaseComObject(thisRange);              
            }

        }

        public static Excel.Workbook FindActiveWorkbook()
        {
            GlobalFunctions.WaitForApplicationReady();
            Excel.Workbook currWb = null;
            try
            {
                if (Globals.ThisAddIn.Application.Workbooks.Count > 1)
                {
                    foreach (Excel.Workbook wb in Globals.ThisAddIn.Application.Workbooks)
                    {                    
                        currWb = wb;                   
                        if (wb.Name == ActiveWBName || wb.Name == ActiveWBName + ActiveWBExt)
                        {
                            if (wb.Path == ActiveWBPath)
                            {
                                return wb;
                            }
                        }
                        Marshal.ReleaseComObject(currWb);
                    }
                }
                else
                {
                    currWb = Globals.ThisAddIn.Application.ActiveWorkbook;
                    String currWBName = removeExtension(currWb.Name);                 
                    if (currWBName == ActiveWBName)
                    {                     
                        if (currWb.Path == ActiveWBPath)
                        {
                            return currWb;
                        }
                        else
                        {                          
                            //return null;
                            return currWb;
                        }
                    }
                    else
                    {                      
                        Globals.ThisAddIn.DisableSync();
                        Globals.Ribbons.Ribbon1.SetRibbonDisconnected();
                        throw new Exception("Incorrect ActiveWB Name - Expected: '" + ActiveWBName + "' Got " + currWBName);
                    }
                }
                throw new Exception("Workbook not found");
            }
            catch (Exception ex)
            {
                if (currWb != null) Marshal.ReleaseComObject(currWb);
                throw;
            }

        }
        public static Excel.Worksheet findWorksheetByName(String wsName)
        {
            Excel.Worksheet currSheet = null;
            Excel.Workbook activeWb = null;
            try
            {
                activeWb = FindActiveWorkbook();
                foreach (Excel.Worksheet ws in activeWb.Worksheets)
                {
                    currSheet = ws;
                    if (ws.Name.ToUpper() == wsName.ToUpper())
                    {
                        return ws;
                    }
                    else
                    {
                        Marshal.ReleaseComObject(ws);
                    }
                }
                return null;
            }
            catch (Exception)
            {
                if (currSheet != null) Marshal.ReleaseComObject(currSheet);
                if (activeWb != null) Marshal.ReleaseComObject(activeWb);
                throw;
            }

        }

     

        public static SqlConnection GetDBConnection()
        {

            return dConn;
        }

        public static DateTime MaxDate(DateTime date1, DateTime date2)
        {
            if (date1 >= date2)
            {
                return date1;
            }
            else
            {
                return date2;
            }

        }

        public static void SetActiveWorkBookName()
        {
            String oName;
            String oExt = "";


            Excel.Workbook ActiveWB = Globals.ThisAddIn.Application.ActiveWorkbook;
            oName = ActiveWB.Name;
            ActiveWBPath = ActiveWB.Path;
            ActiveWBFullPath = ActiveWB.FullName;
            if (oName == null)
            {
                throw new Exception("Could not read workbook name");
            }


            if (oName.Contains('.'))
            {             
                Int32 dotIndex = oName.LastIndexOf('.');            
                oExt = oName.Substring(dotIndex + 1, oName.Length - dotIndex - 1);            
                if (!(oExt == "xlsb" || oExt == "xlsm" || oExt == "xls" || oExt == "xlsm" || oExt == "csv" || oExt == "xlsx"))
                {
                    throw new Exception("Unrecognized Extension: " + oExt);
                }
                oExt = "." + oExt;
                oName = oName.Substring(0, dotIndex);
            }
            _activeWBExt = oExt;
            _activeWBName = oName;
        }


        #region SQL Commands
        public static void AddSQLParameter(ref SqlCommand cmd, SqlDbType datatype, String paramName, Object paramValue)
        {
            switch (datatype)
            {
                case SqlDbType.DateTime2:
                    cmd.Parameters.Add(paramName, SqlDbType.DateTime2);
                    cmd.Parameters[paramName].Value = paramValue.ToString();
                    break;
                case SqlDbType.DateTime:
                    cmd.Parameters.Add(paramName, SqlDbType.DateTime);
                    cmd.Parameters[paramName].Value = paramValue.ToString();
                    break;
                case SqlDbType.VarChar:
                    cmd.Parameters.Add(paramName, SqlDbType.VarChar, 255);
                    cmd.Parameters[paramName].Value = paramValue.ToString();
                    break;
                case SqlDbType.Variant:
                    cmd.Parameters.Add(paramName, SqlDbType.Variant);
                    cmd.Parameters[paramName].Value = paramValue;
                    break;
                default:
                    throw new Exception("Unrecognized SqlDbType");

            }




        }
        public static void AppendEqualityQueryClause(ref String input, String paramName)
        {
            AppendEqualityQueryClause(ref input, paramName, "@" + paramName);
        }
        public static void AppendEqualityQueryClause(ref String input, String paramName, String varName)
        {           
            AppendOperatorClause(ref input, paramName, varName, "=");
        }
        public static void AppendOperatorClause(ref String input, String paramName, String _operator)
        {
            AppendOperatorClause(ref input, paramName, "@" + paramName, _operator);
        }
        public static void AppendOperatorClause(ref String input, String paramName, String varName, String _operator)
        {
            AppendWhere(ref input);
            input = input  + paramName + " " + _operator + " " + varName;

        }
        private static void AppendWhere(ref String input)
        {
            String wherePrefix;
            if (input.Contains(" where "))
            {
                wherePrefix = " and ";
            }
            else
            {
                wherePrefix = " where ";
            }

            input = input + wherePrefix;

        }
        #endregion  
        
        public static String removeExtension(string fileName)
        {
            if (fileName.Contains('.'))
            {
                Int32 dotIndex = fileName.LastIndexOf('.');
                fileName = fileName.Substring(0, dotIndex);
            }
            return fileName;

        }
            
        public static void ConnectToDB()
        {

            try
            {
                string assemblyLoc = AppDomain.CurrentDomain.BaseDirectory;
                String configLoc = Path.Combine(assemblyLoc, "ExcelSync.dll.config");

                System.Xml.XmlDocument configXML = new System.Xml.XmlDocument();
                configXML.Load(configLoc);
                System.Xml.XmlNode xNode = configXML.SelectSingleNode("configuration/connectionStrings/add");
                String DBConnectionString = xNode.Attributes["connectionString"].Value;
                dConn = new SqlConnection(DBConnectionString);
            }
            catch (Exception ex)
            {
                ErrorLog(ex);
            }




        }

        public static DataTable GetShareDBData(SqlCommand cmd, String TableName)
        {
            try
            {

                using (SqlDataAdapter ShareDataAdapter = new SqlDataAdapter(cmd))
                {

                    DataSet ShareDS = new DataSet();
                    ShareDataAdapter.Fill(ShareDS, TableName);
                    DataTable ShareDT = ShareDS.Tables[TableName];
                    return ShareDT;
                }

            }
            catch (Exception ex)
            {
                ErrorLog(ex);
                return null;
            }


        }

        public static void WaitForApplicationReady()
        {
            CheckEditMode();
            CheckCopyMode();
            CheckAppReady();                 
        }

        private static void CheckAppReady(Int32 callCounter = 0)
        {
             try
             {
                while (true)
                {
                    if (callCounter > 50)
                    {
                        throw new Exception("Application not ready for > 10 seconds");
                    }

                    if (Globals.ThisAddIn.Application.Ready)
                    {
                        return;
                    }

                    Thread.Sleep(200);
                    callCounter++;
                }
            }
            catch (COMException)
            {
                Thread.Sleep(200);
                callCounter = callCounter + 1;
                CheckAppReady(callCounter);
            }
        }
        private static void CheckEditMode(Int32 callCounter = 0)
        {
             try
            {
                while (true)
                {
                    if (callCounter > 50)
                    {
                        throw new Exception("Application in edit mode > 10 seconds");
                    }

                    if (IsInEditMode())
                    {
                        Thread.Sleep(200);
                        callCounter = callCounter + 1;
                    }
                    else
                    {
                        return;
                    }
                }
            }
             catch (COMException)
             {
                 Thread.Sleep(200);
                 callCounter = callCounter + 1;
                 CheckEditMode(callCounter);
             }
        }
        private static void CheckCopyMode(Int32 callCounter = 0)
        {
            try
            {
                while (true)
                {
                    if (callCounter > 50)
                    {
                        throw new Exception("Application in copy mode > 10 seconds");
                    }

                    if (Globals.ThisAddIn.Application.CutCopyMode == Excel.XlCutCopyMode.xlCopy ||
                        Globals.ThisAddIn.Application.CutCopyMode == Excel.XlCutCopyMode.xlCut)
                    {
                        Thread.Sleep(200);
                        callCounter = callCounter + 1;
                    }                      
                    else
                    {
                        return;
                    }
                }
            }
            catch (COMException)
            {
                Thread.Sleep(200);
                callCounter = callCounter + 1;
                CheckCopyMode(callCounter);
            }

        }
        private static bool IsInEditMode()
        {
            const int menuItemType = 1;
            const int newMenuId = 18;

            try
            {
                Microsoft.Office.Core.CommandBarControl newMenu =
                    Globals.ThisAddIn.Application.CommandBars["Worksheet Menu Bar"].FindControl(menuItemType, newMenuId, Type.Missing, Type.Missing, true);
                return newMenu != null && !newMenu.Enabled;
            }
            catch(Exception)
            {
                return true;
            }          
        }

        public static Array ResizeArray(Array arr, int[] newSizes)
        {
            if (newSizes.Length != arr.Rank)
                throw new ArgumentException("arr must have the same number of dimensions " +
                                            "as there are elements in newSizes", "newSizes");

            var temp = Array.CreateInstance(arr.GetType().GetElementType(), newSizes);
            int length = arr.Length <= temp.Length ? arr.Length : temp.Length;
            Array.ConstrainedCopy(arr, 0, temp, 0, length);
            return temp;
        }


#region "Log"
        public static void InfoLog(String message, CellUpdate debugObject = null)
        {
            if (!EnableInfoLogging)
            {
                return;
            }

            StringBuilder infoDesc = new StringBuilder();
          

            infoDesc.AppendLine("Info @ " + DateTime.Now);           
            infoDesc.AppendLine(message);
            String oDebug = "";              
            if (debugObject != null)
            {
                oDebug = GetCellDebug(debugObject);
            }
            if (oDebug != "") infoDesc.AppendLine(oDebug);
            Log(infoDesc.ToString(), infoLogFileName);
        }
        public static void ErrorLog(Exception incEx, CellUpdate debugObject = null)
        {
            String oDebug = "";
            if (debugObject != null)
            {
                oDebug = GetCellDebug(debugObject);
            }

            StringBuilder ExceptionDesc = new StringBuilder();
            ExceptionDesc.AppendLine("Exception @ " + DateTime.Now);
            if (oDebug != "") ExceptionDesc.AppendLine(oDebug);
            ExceptionDesc.AppendLine(Vars.SessionID);
            ExceptionDesc.AppendLine(incEx.Message + " " + incEx.StackTrace);
            Log(ExceptionDesc.ToString(), errorLogFileName);

        }
        private static void Log(string msg, String fileName)
        {
            String filepath = "";
            if (!String.IsNullOrEmpty(ActiveWBPath))
            {
                filepath = ActiveWBPath;
            }
            else
            {
                   filepath = Vars.AssemblyDirectory;
            }

            filepath = Path.Combine(filepath, "ExcelSync");
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

        public static String GetCellDebug(CellUpdate cs)
        {
            String ret = String.Format("{0}!({1},{2})", cs.Worksheet, cs.Row, cs.Col);
            ret += String.Format(" {0}: {1}", cs.Type, cs.val);
            if (cs.changeTime != DateTime.MinValue)
            {
                ret += " [Change Time: " + cs.changeTime.ToString("dd MMM yyyy HH:mm:ss.fff") + "]";
            }
            return ret;
        }
        private static void CreateFolderSafe(string path)
        {
            if (!Directory.Exists(path))
            {
                Directory.CreateDirectory(path);
            }
        }
#endregion




        public class DecayingQueue<T>
        {

            public readonly ConcurrentQueue<Tuple<T, DateTime>> queue = new ConcurrentQueue<Tuple<T, DateTime>>();

            public int DecayMilliseconds { get; private set; }

            public DecayingQueue(int decayMilliseconds)
            {
                DecayMilliseconds = decayMilliseconds;
            }
            

            public void Decay()
            {
                DateTime DecayTime = DateTime.Now.AddMilliseconds(-DecayMilliseconds);
                Boolean keepGoing = true;
                lock (this)
                {
                    Tuple<T, DateTime> peekObj;
                    while (queue.TryPeek(out peekObj) & keepGoing)
                    {
                        DateTime objTime = peekObj.Item2;
                        if (objTime <= DecayTime)
                        {
                            Tuple<T, DateTime> outObj;
                            queue.TryDequeue(out outObj);
                        }
                        else
                        {
                            keepGoing = false;
                        }

                    }
                }

            }

            public Tuple<T, DateTime> Dequeue()
            {
                Tuple<T, DateTime> outObj;
                queue.TryDequeue(out outObj);
                return outObj;
            }

            public void Enqueue(T obj)
            {
                Tuple<T, DateTime> queueObj = new Tuple<T, DateTime>(obj, DateTime.Now);
                queue.Enqueue(queueObj);
                Decay();
            }
            public void Enqueue(Tuple<T, DateTime> obj)
            {
                queue.Enqueue(obj);               
            }

        }
    }


}
