using System;
using System.Diagnostics;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Excel = Microsoft.Office.Interop.Excel;
using Office = Microsoft.Office.Core;
using System.Runtime.InteropServices;

namespace ExcelSync
{
    public class CellReader
    {
        public static HashSet<CellUpdate> GetAllCells()
        {
            Excel.Worksheet currSheet = null;
            Excel.Sheets sheets = null;
            Excel.Workbook activeWb = null;

            HashSet<CellUpdate> myCells = new HashSet<CellUpdate>();
            //System.Diagnostics.Stopwatch stopwatch = new System.Diagnostics.Stopwatch();
            //stopwatch.Start();

            try
            {
                activeWb = GlobalFunctions.FindActiveWorkbook();

                sheets = activeWb.Worksheets;
                foreach (Excel.Worksheet ws in sheets)
                {
                    currSheet = ws;

                    String wsName = currSheet.Name.ToUpper();
                    Int32 LastCol;
                    Int32 LastRow;
                    WorksheetExt wsBoundary;
                    if (GlobalFunctions.worksheetBounds.Keys.Contains(wsName))
                    {
                        wsBoundary = GlobalFunctions.worksheetBounds[wsName];
                    }
                    else
                    {
                        wsBoundary = new WorksheetExt(wsName);
                        GlobalFunctions.worksheetBounds.Add(wsName, wsBoundary);
                    }
                    LastCol = wsBoundary.LastCol;
                    LastRow = wsBoundary.LastRow;


                    Excel.Range range = ws.Range["A1", GlobalFunctions.GetExcelColumnName(LastCol) + LastRow];
                    
                    // FOR LOOP ON VALUES  
                    dynamic formulaArrayD = range.Formula;

                    GlobalFunctions.WaitForApplicationReady();
                    Object[,] formulaArray = formulaArrayD as Object[,];
                    String[,] commentArray = GetComments(wsName, range, LastRow, LastCol);                    

                    if (formulaArray != null)
                    {
                        for (int i = 1; i <= LastRow; i++)
                        {
                            for (int j = 1; j <= LastCol; j++)
                            {                                   
                                if (formulaArray[i, j] != null)
                                {
                                    if (formulaArray[i, j].ToString() != "")
                                    {
                                        CellUpdate uc = new CellUpdate(i, j, wsName, formulaArray[i, j].ToString(), Enums.CellChangeType.Value, "", DateTime.MinValue);
                                        myCells.Add(uc);
                                    }
                                }
                            }
                        }
                    }

                    if (commentArray != null)
                    {
                        for (int i = 1; i <= LastRow; i++)
                        {
                            for (int j = 1; j <= LastCol; j++)
                            {
                                if (commentArray[i, j] != null)
                                {
                                    if (commentArray[i, j] != "")
                                    {
                                        CellUpdate uc = new CellUpdate(i, j, wsName, commentArray[i, j].ToString(), Enums.CellChangeType.Comment, "", DateTime.MinValue);
                                        myCells.Add(uc);
                                    }
                                }
                            }
                        }
                    }

                    
                    if (range != null) Marshal.ReleaseComObject(range);
                    if (ws != null) Marshal.ReleaseComObject(ws);

                }
                return myCells;
            }
            catch (Exception ex)
            {
                if (ex.Message == "Incorrect ActiveWB Name")
                {
                     Globals.ThisAddIn.DisableSync();
                }
                throw;
            }
            finally
            {
                if (currSheet != null) Marshal.ReleaseComObject(currSheet);
                if (sheets != null) Marshal.ReleaseComObject(sheets);
                if (activeWb != null) Marshal.ReleaseComObject(activeWb);
                //GlobalFunctions.InfoLog(String.Format("GetAllCells: {0}", stopwatch.Elapsed));
            }
        }
        private static String[,] GetComments(String sheetName, Excel.Range range, int lastRow, int lastCol)
        {
            String[,] commentArray = Array.CreateInstance(typeof(String), new int[2] { lastRow, lastCol }, new int[2] { 1, 1 }) as String[,];
            Excel.Range commentCells = null;
            Excel.Comment comment = null;
            //Excel.Shape cShape = null;

            try
            {
                commentCells = searchComments(range);
                if (commentCells == null)
                {
                    return commentArray;
                }

                //System.Diagnostics.Stopwatch stopwatch = new System.Diagnostics.Stopwatch();
                //stopwatch.Start();
                //int ccount = 0;
                foreach (Excel.Range singleCell in commentCells)
                {
                    comment = singleCell.Comment;
                    if (comment != null)
                    {
                        String author = comment.Author;
                        String text = comment.Text();
                        commentArray[singleCell.Row, singleCell.Column] = text;
                    }
                    else
                    {
                        //GlobalFunctions.InfoLog("Empty Comment", new CellUpdate(singleCell, Enums.CellChangeType.Comment));
                    }
                    //cShape = comment.Shape;         
                    //ccount++;
                  
                }

                //stopwatch.Stop();
                //GlobalFunctions.InfoLog(String.Format("Sheet: {0}, Comments: {1}, Time elapsed: {2}", sheetName, ccount, stopwatch.Elapsed));                
                return commentArray;
            } 
            catch(Exception)
            {
                throw;
            }
            finally
            {
                if (commentCells != null) Marshal.ReleaseComObject(commentCells);
                if (comment != null) Marshal.ReleaseComObject(comment);
            }
        }      
        private static Excel.Range searchComments(Excel.Range range)
        {
            try
            {
                Excel.Range comments = range.SpecialCells(Excel.XlCellType.xlCellTypeComments);
                return comments;
            }
            catch(System.Runtime.InteropServices.COMException ex)
            {
                if (ex.Message == "No cells were found.")
                {
                    return null;
                }
                else
                {
                    throw;
                }
            }
        }
        
    }
}
