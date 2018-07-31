using System;
using Excel = Microsoft.Office.Interop.Excel;
using System.Runtime.InteropServices;

namespace ExcelSync
{
    public class WorksheetExt
    {      
        public readonly String SheetName;
        public Int32 LastRow { get; private set; }
        public Int32 LastCol { get; private set; }
        //public CellStyleExt[,] CellStyles;

        public WorksheetExt(String sheetName)
        {
            SheetName = sheetName;
            Excel.Worksheet ws = null;
            try
            {
                ws = GlobalFunctions.findWorksheetByName(SheetName);
                LastCol = ws.UsedRange.Columns.Count < 20 ? 40 : GetLastColumn(ws) + 20;
                LastRow = ws.UsedRange.Rows.Count < 20 ? 40 : GetLastRow(ws) + 20;
                //CellStyles = new CellStyleExt[LastRow + 1, LastCol + 1];
            }
            catch (Exception ex)
            {
                GlobalFunctions.ErrorLog(ex);
                if (ws != null) Marshal.ReleaseComObject(ws);
                throw;
            }           
        }

        public void UpdateLastRow(Int32 RowNum)
        {
            LastRow = RowNum;
            //if (LastRow + 1 > CellStyles.GetLength(0))
            //{
            //    Int32[] SizeArray = new Int32[2];
            //    SizeArray[0] = LastRow + 1;
            //    SizeArray[1] = LastCol + 1;
            //    CellStyles =  (CellStyleExt[,])GlobalFunctions.ResizeArray(CellStyles, SizeArray);
            //}
        }
        public void UpdateLastCol(Int32 ColNum)
        {
            LastCol = ColNum;
            //if (LastCol + 1 > CellStyles.GetLength(1))
            //{
            //    Int32[] SizeArray = new Int32[2];
            //    SizeArray[0] = LastRow + 1;
            //    SizeArray[1] = LastCol + 1;
            //    CellStyles = (CellStyleExt[,])GlobalFunctions.ResizeArray(CellStyles, SizeArray);
            //}
        }

        private static Int32 GetLastRow(Excel.Worksheet ws)
        {
            try
            {
                Int32 lastRowIgnoreFormulas = ws.Cells.Find(
                     "*",
                     System.Reflection.Missing.Value,
                     Excel.XlFindLookIn.xlValues,
                     Excel.XlLookAt.xlWhole,
                     Excel.XlSearchOrder.xlByRows,
                     Excel.XlSearchDirection.xlPrevious,
                     false,
                     System.Reflection.Missing.Value,
                     System.Reflection.Missing.Value).Row;
                return lastRowIgnoreFormulas;
            }
            catch(Exception)
            {
                return 25;
            }
         

        }

        private static Int32 GetLastColumn(Excel.Worksheet ws)
        {
            try
            {
                int lastColIgnoreFormulas = ws.Cells.Find(
                      "*",
                      System.Reflection.Missing.Value,
                      System.Reflection.Missing.Value,
                      System.Reflection.Missing.Value,
                      Excel.XlSearchOrder.xlByColumns,
                      Excel.XlSearchDirection.xlPrevious,
                      false,
                      System.Reflection.Missing.Value,
                      System.Reflection.Missing.Value).Column;
                return lastColIgnoreFormulas;
             }
            catch(Exception)
            {
                return 25;
            }
        }

    }


}
