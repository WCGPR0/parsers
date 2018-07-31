using System;
using System.Collections.Generic;
using System.Linq;
using Excel = Microsoft.Office.Interop.Excel;
using Office = Microsoft.Office.Core;
using System.Data.SqlClient;
using System.Data;
using System.Runtime.InteropServices;
using System.Globalization;

namespace ExcelSync
{
    public partial class ThisAddIn
    {

       HashSet<CellUpdate> MySavedCells;
       Int32 RefreshDelayCountdown = 2;
     // GlobalFunctions.DecayingQueue<Int32> updateCount = new GlobalFunctions.DecayingQueue<Int32>(60000);
       Int32 MyRefreshTime = 5000;
       SqlCommand SelectCommand;
       SqlCommand OthersSelectCommand ;
       SqlCommand InsertCommand;
       SqlCommand UpdateCommand;

       public String channelID;
       public String channelConnectionID;
      
 
       System.Timers.Timer pollTimer = new System.Timers.Timer(5000);

     
       //public Int32 DeterminePollTime()
       //{
       //    Int32 UpperSecondBound = 10;
       //    Int32 LowerSecondBound = 1;



       //    GlobalFunctions.DecayingQueue<Int32> newQueue = new GlobalFunctions.DecayingQueue<Int32>(updateCount.DecayMilliseconds);

       //    Int32 ucCount = updateCount.queue.Count;
       //    Int32 UpperPointBound = Math.Max(1, ucCount * 60);
       //    Int32 AllPoints = 0;


       //    Tuple<Int32, DateTime> queueObj = updateCount.Dequeue();
       //    DateTime referenceTime = DateTime.Now.AddMilliseconds(-1 * updateCount.DecayMilliseconds);
       //    while (queueObj != null)
       //    {
       //        DateTime updateTime = queueObj.Item2;
       //        Int32 pointVal = (Int32)Math.Round((updateTime - referenceTime).TotalSeconds);
       //        AllPoints += Math.Max(pointVal, 0);
       //        Tuple<Int32, DateTime> newQueueObj = new Tuple<Int32, DateTime>(1, updateTime);
       //        newQueue.Enqueue(newQueueObj);
       //        queueObj = updateCount.Dequeue();
       //    }


       //    Double thisPointVal = (Double)(UpperPointBound - AllPoints) / (Double)UpperPointBound;
       //    Int32 AddToLowerBound = (Int32)Math.Round(thisPointVal * (UpperSecondBound - LowerSecondBound), 0, MidpointRounding.AwayFromZero);

       //    updateCount = newQueue;
       //    return LowerSecondBound + AddToLowerBound;

       //}

       public Boolean TryConnectSheet()
       {
           try
           {
               GlobalFunctions.SetActiveWorkBookName();
               String channelName;
               if (CheckConnection(out channelName))
               {
                   Globals.Ribbons.Ribbon1.buttonToggleConnect.Checked = true;
                   Globals.Ribbons.Ribbon1.SetRibbonConnected();
                   Globals.Ribbons.Ribbon1.editBoxChannel.Text = channelName;
                   EnableSync();
                   return true;
               }
               else
               {
                   DisableSync();
                   Globals.Ribbons.Ribbon1.SetRibbonDisconnected();
                   return false;

               }
           }
           catch(Exception ex)
           {
               try
               {
                   GlobalFunctions.ErrorLog(ex);
                   DisableSync();
                   Globals.Ribbons.Ribbon1.SetRibbonDisconnected();
               }
               catch(Exception)
               {

               }
               return false;
           }


       }
       private Boolean CheckConnection(out String channelName)
       {
           channelName = "";
           try
           {
               SqlConnection dConn = GlobalFunctions.GetDBConnection();
               String qry = "Select * from Channels";
               GlobalFunctions.AppendEqualityQueryClause(ref qry, "User_Server");
               GlobalFunctions.AppendEqualityQueryClause(ref qry, "Workbook_Name");
               SqlCommand cmd = new SqlCommand(qry, dConn);
               GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.VarChar, "@User_Server", System.Environment.MachineName);
               GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.VarChar, "@Workbook_Name", GlobalFunctions.ActiveWBName);
               //GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.VarChar, "@Workbook_Path", ActiveWB.Path);

               DataTable ShareDT = GlobalFunctions.GetShareDBData(cmd, "Channels");
               foreach (DataRow dr in ShareDT.Rows)
               {
                   channelName = dr["Channel"].ToString();
                   channelID = dr["Channel_ID"].ToString();
                   Vars.UserID = "WORKBOOK_NAME:" + dr["Workbook_Name"].ToString() + ";USER_SERVER=" + dr["User_Server"].ToString() + ";ID:" + dr["ID"].ToString();
                   return true;
                }             
               return false;

           }
           catch (Exception ex)
           {
               GlobalFunctions.ErrorLog(ex);             
               return false;
           }

       }

       private void ThisAddIn_Startup(object sender, System.EventArgs e)
        {
           try
           {            
               GlobalFunctions.ConnectToDB();       
               // Workbook open event does not run when debugging from visual studop
               if (System.Diagnostics.Debugger.IsAttached)
               {
                   TryConnectSheet();
               }

               GlobalFunctions.InfoLog("Session Started ID: " + Vars.SessionID);
               Vars.LatestUpdateTime = Convert.ToDateTime(getDbTime()).AddSeconds(-5); 
               Application.WorkbookOpen += Application_WorkbookOpen;

           }
           catch (Exception ex)
           {
               GlobalFunctions.ErrorLog(ex);
           }

        }

       void Application_WorkbookOpen(Excel.Workbook Wb)
       {
           //Do not run if ribbon is already connected
           if (Globals.Ribbons.Ribbon1.Connected == false)
           {
               TryConnectSheet();
           }
       }

       public void EnableSync()
       {
           //Start with current cells as base
          
           Application.SheetChange += Application_SheetChange;

           MySavedCells = new HashSet<CellUpdate>(CellReader.GetAllCells().Where(c => c.val != ""));

           pollTimer.Enabled = true;
           pollTimer.Elapsed += pollTimer_Elapsed;
           pollTimer.Start();
       
       }

       void SpeedUpRefresh()
       {
           MyRefreshTime = 2000;
           RefreshDelayCountdown = 2;
           //RefreshTimerLabel();
       }
       void SlowDownRefresh()
       {
           MyRefreshTime = 5000;
           RefreshDelayCountdown = -1;
           //RefreshTimerLabel();
       }

       void Application_SheetChange(object Sh, Excel.Range Target)
       {
           //updateCount.Enqueue(1);
           //worksheetBoundsInvalidated = true;
           SpeedUpRefresh();
           
       }

     
     
       public void DisableSync()
       {
           try
           {
               Application.SheetChange -= Application_SheetChange;
               pollTimer.Enabled = false;
               pollTimer.Elapsed -= pollTimer_Elapsed;
           }
           catch (Exception ex)
           {
               GlobalFunctions.ErrorLog(ex);
           }
       }




       //public void ClearNotifications()
       //{
       //    Excel.Worksheet ws = null;
       //    try
       //    {
       //        foreach (KeyValuePair<String, WorksheetExt> kvp in GlobalFunctions.worksheetBounds)
       //        {
       //            WorksheetExt wext = kvp.Value;
       //            ws = GlobalFunctions.findWorksheetByName(kvp.Key);

       //            for (int i = 1; i <= wext.LastRow; i++)
       //            {
       //                for (int j = 1; j <= wext.LastCol; j++)
       //                {
       //                    if (wext.CellStyles[i, j] != null)
       //                    {
       //                        CellStyleExt cext = wext.CellStyles[i, j];
       //                        CellUpdate uc = new CellUpdate(i, j, kvp.Key, cext);
       //                        GlobalFunctions.ClearCell(uc, ws);
       //                        wext.CellStyles[i, j] = null;
       //                    }
       //                }
       //            }
       //        }
       //    }
       //    catch (Exception ex)
       //    {
       //        GlobalFunctions.ErrorLog(ex);
       //    }
       //    finally
       //    {
       //        if (ws != null) Marshal.ReleaseComObject(ws);

       //    }
       //}


        
       private void pollTimer_Elapsed(object sender, System.Timers.ElapsedEventArgs e)
       {
           try
           {
               pollTimer.Stop();
               Globals.Ribbons.Ribbon1.LabelStatus.Label = "Syncing. . .";
               GlobalFunctions.WaitForApplicationReady();
               DoWork();
            
           }
           catch (Exception ex)
           {
               GlobalFunctions.ErrorLog(ex);
           }
           finally
           {
               pollTimer.Start();
               Globals.Ribbons.Ribbon1.LabelStatus.Label = "Sync Complete. . .";            
               pollTimer.Interval = MyRefreshTime;
           }

       }
       private void DoWork()
       {
           try
           {
               Application.Calculation = Excel.XlCalculation.xlCalculationManual;

               DateTime SyncStart = DateTime.Now;
               if (RefreshDelayCountdown == 0)
               {
                   SlowDownRefresh();
               }
               else
               {
                   if (RefreshDelayCountdown > 0)
                   {
                       RefreshDelayCountdown--;
                   }
               }
               List<CellUpdate> otherCells = GetDBCells();
               HashSet<CellUpdate> myChanges = GetChangedCells();
               foreach (CellUpdate myNewCell in myChanges)
               {
                   //GlobalFunctions.worksheetBounds[myNewCell.Worksheet].CellStyles[myNewCell.Row, myNewCell.Col] = new CellStyleExt();

                   /* Remove if updating a saved cell */
                   CellUpdate savedCellOld = FindWithoutValue(MySavedCells, myNewCell);
                   CellUpdate OthersCellConflict = FindWithoutValue(otherCells, myNewCell);
                   if (OthersCellConflict != null)
                   {
                       if (OthersCellConflict.val == "")
                       {
                           otherCells.Remove(OthersCellConflict);
                           /* Other deleted, you win */
                       }
                       else
                       {
                           /* Both have values, you win */
                           otherCells.Remove(OthersCellConflict);
                       }

                   }

                   //Save update             
                   CommitUpdates(myNewCell);
                   if (savedCellOld != null) MySavedCells.Remove(savedCellOld);
                   if (myNewCell.val != "") MySavedCells.Add(myNewCell);
                   try
                   {
                   if (string.IsNullOrEmpty(myNewCell.changeAuthor)) Globals.Ribbons.Ribbon1.label1.Label = "self";  
                   Globals.Ribbons.Ribbon1.label2.Label =  myNewCell.Worksheet.ToString() + ':' + myNewCell.Row.ToString() + '/' + myNewCell.Col.ToString();
                   Globals.Ribbons.Ribbon1.label3.Label = myNewCell.val.ToString();
                   }
                   catch(Exception)
                   { 
                   }
               }




               foreach (CellUpdate otherNewCell in otherCells)
               {
                   /* Remove if updating a saved cell */
                   CellUpdate savedCellOld = FindWithoutValue(MySavedCells, otherNewCell);

                   //Update my sheet
                   RefreshSheet(otherNewCell);
                   //GlobalFunctions.InfoLog("Update Received", otherNewCell);
                   if (savedCellOld != null) MySavedCells.Remove(savedCellOld);
                   if (otherNewCell.val != "") MySavedCells.Add(otherNewCell);
                   try
                   {
                       if (string.IsNullOrEmpty(otherNewCell.changeAuthor))
                       {
                           Globals.Ribbons.Ribbon1.label1.Label = "unknown!";
                       }
                       else
                       {
                           Globals.Ribbons.Ribbon1.label1.Label = otherNewCell.changeAuthor.ToString().Substring(1, 10);
                       }
                       Globals.Ribbons.Ribbon1.label2.Label = otherNewCell.Worksheet.ToString() + ':' + otherNewCell.Row.ToString() + '/' + otherNewCell.Col.ToString();
                       Globals.Ribbons.Ribbon1.label3.Label = otherNewCell.val.ToString();
                   }
                   catch (Exception)
                   {
                   }
               }


               //DateTime SyncEnd = DateTime.Now;
               //TimeSpan SyncDuration = (SyncEnd - SyncStart);
               //GlobalFunctions.InfoLog("Sync Duration: " + SyncDuration.TotalSeconds.ToString() + " seconds");
           }
           finally
           {
               Application.Calculation = Excel.XlCalculation.xlCalculationAutomatic;
           }
       }

     

     
       private void ThisAddIn_Shutdown(object sender, System.EventArgs e)
        {
        }

     

       private HashSet<CellUpdate> GetChangedCells()
       {
           HashSet<CellUpdate> AllMyCells = CellReader.GetAllCells();
           HashSet<CellUpdate> myChanges = new HashSet<CellUpdate>(AllMyCells.Except(MySavedCells));  
         
           // Get all cells no longer being used that previously had a value
           IEnumerable<CellUpdate> deletedCellsTemp = MySavedCells.Except(AllMyCells);
           for (int i = 0; i < deletedCellsTemp.Count(); i++)
           {
               /* CELL BEFORE */
               CellUpdate oldCell = deletedCellsTemp.ElementAt(i);
               CellUpdate currCell = FindWithoutValue(AllMyCells, oldCell);

               if (currCell == null)
               {
                   CellUpdate newUC = new CellUpdate(oldCell.Row, oldCell.Col, oldCell.Worksheet, "", oldCell.TypeEnum, oldCell.changeAuthor, DateTime.Now);
                   myChanges.Add(newUC);
               }
           }
          
           return myChanges;
       }
      
        
       private void CommitUpdates(CellUpdate oneCell)
       {
           SqlCommand cmd = GetSelectCommand();
           GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.Variant, "@Channel_ID", channelID);
           GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.Variant, "@Excel_Row", oneCell.Row);
           GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.Variant, "@Excel_Column", oneCell.Col);
           GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.Variant, "@Change_Type", oneCell.Type);
           GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.Variant, "@Worksheet", oneCell.Worksheet);

           using (SqlDataAdapter ShareDataAdapter = new SqlDataAdapter(cmd))
           {
               DataSet ShareDS = new DataSet();
               ShareDataAdapter.Fill(ShareDS);
               DataTable ShareDT = ShareDS.Tables[0];
               DataColumn[] keyColumns = new DataColumn[4];
               keyColumns[0] = ShareDT.Columns["Channel_ID"];
               keyColumns[1] = ShareDT.Columns["Worksheet"];
               keyColumns[2] = ShareDT.Columns["Excel_Row"];
               keyColumns[3] = ShareDT.Columns["Excel_Column"];
               ShareDT.PrimaryKey = keyColumns;
               SqlCommandBuilder cb = new SqlCommandBuilder(ShareDataAdapter);

               DataRow dr = ShareDT.Rows.Find(new object[] { channelID, oneCell.Worksheet, oneCell.Row, oneCell.Col });
               if (dr == null)
               {
                   //if (oneCell.val == "")
                   //{
                   //    //    ShareDataAdapter.DeleteCommand = cb.GetDeleteCommand();
                   //    ShareDataAdapter.InsertCommand = GetInsertCommand(cb);
                   //}
                   //else
                   //{
                   //   
                   //}                   
                   ShareDataAdapter.InsertCommand = GetInsertCommand(cb);
                   Insert(ShareDT, oneCell);
                   ShareDataAdapter.Update(ShareDT);
                   GlobalFunctions.InfoLog("Insert", oneCell);
               }
               else
               {                
                   String changeID = dr["ID"].ToString();
                   Update(changeID, oneCell);
                   GlobalFunctions.InfoLog("Update", oneCell);
               }
               
           }


           SpeedUpRefresh();


       }
       private void Insert(DataTable ShareDT, CellUpdate oneCell)
       {
           DataRow dr = ShareDT.NewRow();
           dr["Channel_ID"] = channelID;
           dr["Worksheet"] = oneCell.Worksheet;
           dr["Excel_Row"] = oneCell.Row;
           dr["Excel_Column"] = oneCell.Col;
           dr["Cell_Value"] = oneCell.val;
           dr["Change_Author"] = Vars.SessionID;
           dr["Change_Time"] = getDbTime();
           dr["Change_Type"] = oneCell.Type;
           ShareDT.Rows.Add(dr);
        }

       private void Update(String changeID, CellUpdate oneCell)
       {        
           SqlCommand updateCmd = GetUpdateCommand();
           updateCmd.Parameters.Add("@ID", SqlDbType.VarChar, 255);
           updateCmd.Parameters["@ID"].Value = changeID;
           updateCmd.Parameters.Add("@Cell_Value", SqlDbType.VarChar, 4095); //Increased size for value
           updateCmd.Parameters["@Cell_Value"].Value = oneCell.val;
           updateCmd.Parameters.Add("@Change_Author", SqlDbType.VarChar, 255);
           updateCmd.Parameters["@Change_Author"].Value = Vars.SessionID;
           updateCmd.Parameters.Add("@Change_Time", SqlDbType.DateTime);
           updateCmd.Parameters["@Change_Time"].Value =getDbTime();
           updateCmd.Connection.Open(); 
           updateCmd.ExecuteNonQuery();
           updateCmd.Connection.Close();
       }
       private List<CellUpdate> GetDBCells()
       {
           try
           {
               SqlCommand cmd = GetOthersChangesSelect();
               GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.DateTime2, "@Change_Time", Vars.LatestUpdateTime.ToShortDateString() + " " + Vars.LatestUpdateTime.TimeOfDay.ToString());
               GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.VarChar, "@channel_id", channelID);
               GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.VarChar, "@Change_Author", Vars.SessionID);     
               DataTable ShareDT = GlobalFunctions.GetShareDBData(cmd, "Changes");
               return ConvertToList(ShareDT);
           }
           catch (Exception ex)
           {
               GlobalFunctions.ErrorLog(ex);
               throw;
           }
       }

       private List<CellUpdate> ConvertToList(DataTable DT)
       {
           Int32 Count = DT.Rows.Count;
           List<CellUpdate> otherCells = new List<CellUpdate>(Count);
           try
           {
               for (int i = 0; i < Count; i++)
               {
                   DataRow dr = DT.Rows[i];
                   CellUpdate newCell = CellUpdate.Create(dr);
                   otherCells.Add(newCell);
               }
               return otherCells;
           }
           catch (Exception ex)
           {
               GlobalFunctions.ErrorLog(ex);
               throw;
           }

       }

       private Boolean RefreshSheet(CellUpdate uc) 
       {
           Excel.Worksheet ws = null;
           Excel.Range thisRange = null;
           Excel.Comment comment = null;

           try
           {
               ws = GlobalFunctions.findWorksheetByName(uc.Worksheet);
               if (ws == null)
               {
                   throw new Exception("Worksheet not found: " + uc.Worksheet);
               }

               GlobalFunctions.WaitForApplicationReady();
               thisRange = GlobalFunctions.createRange(ws, uc.Row, uc.Col);
               if (thisRange != null)
               {
                    CellUpdate oldCell = new CellUpdate(thisRange, uc.TypeEnum);
                    if (!uc.Equals(oldCell))
                    {
                        switch (uc.TypeEnum)
                        {
                            case Enums.CellChangeType.Value:
                                thisRange.Formula = uc.val;
                                break;
                            case Enums.CellChangeType.Comment:
                                comment = thisRange.Comment;
                                if (comment == null)
                                {
                                    thisRange.AddComment(uc.val);
                                }
                                else
                                {
                                    if (String.IsNullOrEmpty(uc.val))
                                    {
                                        thisRange.ClearComments();
                                    }
                                    else
                                    {
                                        comment.Text(uc.val);
                                    }
                                   
                                }
                                break;
                        }
                    }
                    GlobalFunctions.InfoLog("Received", uc);
                    //RefreshedCell rc = new RefreshedCell(thisRange, uc, oldCell.val);
                    //RefreshedCell rc = new RefreshedCell(thisRange, uc, "");
                    Vars.LatestUpdateTime = GlobalFunctions.MaxDate(Vars.LatestUpdateTime, uc.changeTime.AddSeconds(-1));                  
                   
                }
                else
                {
                    Marshal.ReleaseComObject(thisRange);
                }

               return true;
           }
           catch (Exception ex)
           {
               GlobalFunctions.ErrorLog(ex, uc);
               throw ex;
           }
           finally
           {
               if (thisRange != null) Marshal.ReleaseComObject(thisRange);    
               if (ws != null) Marshal.ReleaseComObject(ws);             
           }

       }
   
       private CellUpdate FindWithoutValue(IEnumerable<CellUpdate> searchSet, CellUpdate searchCell)
       {

           foreach (CellUpdate myNewCell in searchSet)
            {
               if (myNewCell.Col == searchCell.Col && myNewCell.Row == searchCell.Row && myNewCell.Worksheet == searchCell.Worksheet && myNewCell.Type == searchCell.Type)
               {
                   return myNewCell;
               }
           }

           return null;
       }


        /* CHANGE TABLE COMMANDS */
        private SqlCommand GetSelectCommand()
        {
            if (SelectCommand == null || true)
            {
                SqlConnection dConn = GlobalFunctions.GetDBConnection();

                String qry = "Select * from Changes";
                GlobalFunctions.AppendEqualityQueryClause(ref qry, "Channel_ID");
                GlobalFunctions.AppendEqualityQueryClause(ref qry, "Excel_Row");
                GlobalFunctions.AppendEqualityQueryClause(ref qry, "Excel_Column");
                GlobalFunctions.AppendEqualityQueryClause(ref qry, "Change_Type");
                GlobalFunctions.AppendEqualityQueryClause(ref qry, "Worksheet");             
                SelectCommand = new SqlCommand(qry, dConn);
            }
            SelectCommand.Parameters.Clear();
            return SelectCommand;

        }
        private SqlCommand GetOthersChangesSelect()
        {
            if (OthersSelectCommand == null)
            {
                String qry = "Select * from Changes";
                GlobalFunctions.AppendOperatorClause(ref qry, "Change_Time", ">=");
                GlobalFunctions.AppendOperatorClause(ref qry, "Change_Author", "<>");
                GlobalFunctions.AppendEqualityQueryClause(ref qry, "channel_id");
                qry += " order by change_time ASC";
                SqlConnection dConn = GlobalFunctions.GetDBConnection();
                OthersSelectCommand = new SqlCommand(qry, dConn);
            }
            OthersSelectCommand.Parameters.Clear();
            return OthersSelectCommand;
        }
        private SqlCommand GetInsertCommand(SqlCommandBuilder cb)
        {
            if (InsertCommand == null)
            {
                InsertCommand = cb.GetInsertCommand();
            }         
            return InsertCommand;

        }
        private SqlCommand GetUpdateCommand()
        {
            if (UpdateCommand == null)
            {
                String UpdateQry = "update Changes set Cell_Value = @cell_value, Change_Author = @change_author, Change_Time = @change_time where ID = @ID";
                SqlConnection dConn = GlobalFunctions.GetDBConnection();
                UpdateCommand = new SqlCommand(UpdateQry, dConn);
            }
            UpdateCommand.Parameters.Clear();           
            return UpdateCommand;
        }

        public DateTime getDbTime()
        {
            var connection = GlobalFunctions.GetDBConnection();
            connection.Open();
            SqlCommand Comm = new SqlCommand("SELECT SYSDATETIME();", connection);
            string dr = Comm.ExecuteScalar().ToString();
            DateTime dbtime = Convert.ToDateTime(dr);
            connection.Close();
            return dbtime.AddHours(-5);
        
        }
        ///dr,"dd/MM/yyyy HH:mm:ss"
        #region VSTO generated code

        /// <summary>
        /// Required method for Designer support - do not modify
        /// the contents of this method with the code editor.
        /// </summary>
        private void InternalStartup()
        {
            Startup += ThisAddIn_Startup;
            Shutdown += ThisAddIn_Shutdown;
        
        }

              
        #endregion
    }
}
