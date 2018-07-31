using System;
using System.Collections.Generic;
using System.Linq;
using System.Data;
using Excel = Microsoft.Office.Interop.Excel;
using Office = Microsoft.Office.Core;
using System.Runtime.InteropServices;

namespace ExcelSync
{
    public class CellUpdate
    {
        public readonly Int32 Row;
        public readonly Int32 Col;
        public readonly String Worksheet;
        public readonly String val;
        public readonly Enums.CellChangeType TypeEnum;
        public readonly String changeAuthor;
        public DateTime changeTime;
        //public CellStyleExt Cext;

        public String Type
        {
            get 
            {
                    switch (TypeEnum)
                    {
                        case Enums.CellChangeType.Value:
                            return "Value";
                        case Enums.CellChangeType.Comment:
                            return "Comment";
                        default:
                            return "Value";
                    }
            }
        }

        //public CellUpdate(Int32 row, Int32 col, String WS, CellStyleExt cext)
        //{
        //    this.Row = row;
        //    this.Col = col;
        //    this.Worksheet = WS;
        //    this.Cext = cext;          
        //}
        public CellUpdate(Int32 row, Int32 col, String WS, String val, Enums.CellChangeType type, String changeAuthor, DateTime changeTime)
        {
            this.Row = row;
            this.Col = col;
            this.Worksheet = WS;
            this.val = val;
            this.TypeEnum = type;
            this.changeTime = changeTime;
            this.changeAuthor = changeAuthor;
            UpdateWSBounds();
        }
        public CellUpdate(Excel.Range cell, Enums.CellChangeType type)
        {
            Excel.Comment comment = null;

            try
            {
                Row = cell.Row;
                Col = cell.Column;
                Worksheet = cell.Worksheet.Name.ToUpper();
                this.TypeEnum = type;
                switch (TypeEnum)
                {
                    case Enums.CellChangeType.Value:
                        val = cell.Formula == null ? "" : cell.Formula.ToString();
                        break;
                    case Enums.CellChangeType.Comment:
                        comment = cell.Comment;
                        val = comment == null ? "" : comment.Text();
                        break;
                }
                UpdateWSBounds();
            }
            catch (Exception ex)
            {
                GlobalFunctions.ErrorLog(ex);
                throw;
            }
            finally
            {
                if (comment != null) Marshal.ReleaseComObject(comment);
            }
           
        }
        public static CellUpdate Create(DataRow dr)
        {
            try
            {
                Int32 iRow = Int32.Parse(dr["Excel_Row"].ToString());
                Int32 iCol = Int32.Parse(dr["Excel_Column"].ToString());
                String iWorksheet = dr["Worksheet"].ToString();
                String ival = dr["Cell_Value"].ToString();
                String iType = dr["Change_Type"].ToString();
                String iChangeAuthor = dr["Change_Author"].ToString();
                DateTime iChangeTime = (DateTime)dr["Change_Time"];
                Enums.CellChangeType type = (iType == "Value" ? Enums.CellChangeType.Value : iType == "Comment" ? Enums.CellChangeType.Comment : Enums.CellChangeType.Value);

                return new CellUpdate(iRow, iCol, iWorksheet, ival, type, iChangeAuthor, iChangeTime);
            }
            catch (Exception ex)
            {
                GlobalFunctions.ErrorLog(ex);
                throw;
            }

        }
        private void UpdateWSBounds()
        {
            Dictionary<String, WorksheetExt> worksheetBounds = GlobalFunctions.worksheetBounds;
            if (worksheetBounds.Keys.Contains(Worksheet))
            {
                WorksheetExt wsBoundary = worksheetBounds[Worksheet];

                Int32 rowBound = Row + 20;
                Int32 colBound = Col + 20;
                if ((rowBound) > wsBoundary.LastRow)
                {
                    wsBoundary.UpdateLastRow(rowBound);
                }
                if ((colBound) > wsBoundary.LastCol)
                {
                    wsBoundary.UpdateLastCol(colBound);
                }
            }
        }
        
        public override Boolean Equals(Object obj)
        {
            CellUpdate uCell = obj as CellUpdate;
            if (uCell == null)
            {
                return false;
            }

            return (uCell.Row == Row && uCell.Col == Col && uCell.Worksheet == Worksheet && uCell.val == val);

        }
        public override int GetHashCode()
        {
            return string.Format("{0}-{1}-{2}-{3}-{4}", Row * 5, Col * 7, Type.ToString().GetHashCode() * 11, Worksheet.GetHashCode() * 13, val.GetHashCode() * 17).GetHashCode();
        }

        

    }
}
