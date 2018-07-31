using System;
using Microsoft.Office.Tools.Ribbon;
using System.Data.SqlClient;
using System.Data;

namespace ExcelSync
{
    public partial class Ribbon1
    {
        public Boolean Connected = false;

        private void Ribbon1_Load(object sender, RibbonUIEventArgs e)
        {              
          
        }


        private void buttonToggleConnect_Click(object sender, RibbonControlEventArgs e)
        {
            if (Connected)
            {
                RemoveConnection();
                SetRibbonDisconnected();
                Globals.ThisAddIn.DisableSync();
            }
            else
            {
                if (!Globals.ThisAddIn.TryConnectSheet())
                {
                    if (CreateConnection())
                    {
                        Globals.ThisAddIn.TryConnectSheet();
                    }                   
                }

            }
        }

        public void SetRibbonConnected()
        {
            buttonToggleConnect.Label = "Disconnect";
            LabelStatus.Label = "Awaiting updates. . .";         
            buttonToggleConnect.Checked = true;
            Connected = true;

        }
        public void SetRibbonDisconnected()
        {
            buttonToggleConnect.Label = "Connect";
            LabelStatus.Label = "";
            buttonToggleConnect.Checked = false;
            Connected = false;
        }

        private String getChannelID(String ChannelName)
        {
            try
            {
                SqlConnection dConn = GlobalFunctions.GetDBConnection();

                String qry = "Select * from Channels Order By Channel_ID asc";

                SqlCommand cmd = new SqlCommand(qry, dConn);          

                DataTable ShareDT = GlobalFunctions.GetShareDBData(cmd, "Channels");

                Int32 MaxChan = 0;
                if (ShareDT.Rows.Count > 0)
                {
                    foreach (DataRow dr in ShareDT.Rows)
                    {
                        String ChannelID = dr["Channel_ID"].ToString();
                        String rChannelName = dr["Channel"].ToString();
                        MaxChan = Math.Max(1, Int32.Parse(ChannelID));
                        if (rChannelName == ChannelName)
                        {
                            return ChannelID;
                        }
                    }
                    return (MaxChan + 1).ToString();                    
                }
                else
                {
                    return "";
                }
            }
            catch (Exception ex)
            {
                GlobalFunctions.ErrorLog(ex);
                throw;
               
            }
        }
        private void RemoveConnection()
        {
            if (Globals.ThisAddIn.channelID == "")
            {
                return;
            }

            String qry = "Select * from channels";
            SqlConnection dConn = GlobalFunctions.GetDBConnection();
            SqlCommand cmd = new SqlCommand(qry, dConn);
            String ActiveWBName = GlobalFunctions.ActiveWBName;
            GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.Variant, "@Channel_ID", Globals.ThisAddIn.channelID);
            GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.Variant, "@Workbook_Name", ActiveWBName);
            GlobalFunctions.AddSQLParameter(ref cmd, SqlDbType.Variant, "@User_Server",System.Environment.MachineName);

            using (SqlDataAdapter ShareDataAdapter = new SqlDataAdapter(cmd))
            {
                DataSet ShareDS = new DataSet();
                ShareDataAdapter.Fill(ShareDS, "Channels");
                DataTable ShareDT = ShareDS.Tables["Channels"];
                DataColumn[] keyColumns = new DataColumn[3];
                keyColumns[0] = ShareDT.Columns["Channel_ID"];
                keyColumns[1] = ShareDT.Columns["Workbook_Name"];
                keyColumns[2] = ShareDT.Columns["User_Server"];
                ShareDT.PrimaryKey = keyColumns;
               SqlCommandBuilder cb = new SqlCommandBuilder(ShareDataAdapter);

               DataRow dr = ShareDT.Rows.Find(new object[] { Globals.ThisAddIn.channelID, ActiveWBName, System.Environment.MachineName });
             if (dr != null)
             {

                 ShareDataAdapter.DeleteCommand = cb.GetDeleteCommand();
                 dr.Delete();
                 ShareDataAdapter.Update(ShareDT);
             } 
            }

        }
        private Boolean CreateConnection()
        {
           if (editBoxChannel.Text == "")
           {
                return false;
           }
           String ChannelID = getChannelID(editBoxChannel.Text);
           if (ChannelID == "")
           {
               ChannelID = "1";
           }

           String qry = "Select * from channels";
           SqlConnection dConn = GlobalFunctions.GetDBConnection();
           SqlCommand cmd = new SqlCommand(qry, dConn);

           using (SqlDataAdapter ShareDataAdapter = new SqlDataAdapter(cmd))
           {
               DataSet ShareDS = new DataSet();
               ShareDataAdapter.Fill(ShareDS, "Channels");
               DataTable ShareDT = ShareDS.Tables["Channels"];
               SqlCommandBuilder cb = new SqlCommandBuilder(ShareDataAdapter);
               
             
               ShareDataAdapter.InsertCommand = cb.GetInsertCommand();
               DataRow dr = ShareDT.NewRow();
               dr["Channel"] = editBoxChannel.Text;
               dr["Channel_ID"] = ChannelID;
               dr["Workbook_Name"] = GlobalFunctions.ActiveWBName;
               dr["User_Server"] = System.Environment.MachineName;
               //dr["Workbook_Path"] = ActiveWB.Path;
               ShareDT.Rows.Add(dr);
               ShareDataAdapter.Update(ShareDT);
           }
           return true;

          
       }

          
    }
}
