using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using SDRDataParserGUI;
using SDRDataParserGUI.Models;
using System.Xml;

public static class DataObtainer
    {

    public const int UPDATE_INTERVAL = 30000; //< Timer constant, the criteria for how often the data gets updated

    public static GraphData data; //< The main instance of the data, use DataObtainer.data to get data

    public static long lastUpdate_ms; //< The time it was last updated in milliseconds
        public static void initialize()
        {
            data = new GraphData(); //< Creates an instance during initialization, should be called during application startup
            update();
        }

        /**
         * Mutator method that updates the data. Runs a request against the middle tier
         */
        public static void update()
        {
            //Updates the main data
            XmlDocument data_xml = new XmlDocument();
            String ret = Functions.MTRequest("AR.MARKET.SHARE.SDR", "", true);
            if (ret != "")
            {
                data_xml.LoadXml(ret);
                XmlNode root = data_xml.SelectSingleNode("report");
                if (root != null)
               {
                data.clear(); //< More efficient than GC, since the space is roughly going to be the same
                foreach (XmlNode transaction in root.SelectNodes("transaction") )
                {
                    GraphData.Transaction myTransaction = new GraphData.Transaction();
                    myTransaction.contract_name = Functions.SelectSingleNodeSafe(transaction, "contract_name");
                    myTransaction.source = Functions.SelectSingleNodeSafe(transaction, "source");
                    myTransaction.type = Functions.SelectSingleNodeSafe(transaction, "type");
                    myTransaction.start_date = Functions.SelectSingleNodeSafe(transaction, "start_date");
                    myTransaction.end_date = Functions.SelectSingleNodeSafe(transaction, "end_date");
                    myTransaction.exec_time = Functions.SelectSingleNodeSafe(transaction, "exec_time");
                    myTransaction.strategy = Functions.SelectSingleNodeSafe(transaction, "strategy");
                    myTransaction.total_volume = Double.Parse(Functions.SelectSingleNodeSafe(transaction, "total_volume"));
                    myTransaction.daily_volume = Double.Parse(Functions.SelectSingleNodeSafe(transaction, "daily_volume"));
                    myTransaction.price = Double.Parse(Functions.SelectSingleNodeSafe(transaction, "price"));
                    myTransaction.premium = Double.Parse(Functions.SelectSingleNodeSafe(transaction, "premium"));
                    myTransaction.strike = Double.Parse(Functions.SelectSingleNodeSafe(transaction, "strike"));
                    myTransaction.unit = Functions.SelectSingleNodeSafe(transaction, "unit");
                    myTransaction.status = Functions.SelectSingleNodeSafe(transaction, "status");
                    myTransaction.report_time = Functions.SelectSingleNodeSafe(transaction, "report_time");
                    data.add(myTransaction);
                }

                //Sets the new update timer
                lastUpdate_ms = DateTime.Now.Ticks / TimeSpan.TicksPerMillisecond; // Updates the timer with DateTimeOffset implementation of time in milliseconds
            }
            }
        } 

    /**
     * Updates the cached data in the background;
     * Returns true if only a short amount of time has passed since last update, otherwise false.
     */
        public static bool updated()
    {
        if ((DateTime.Now.Ticks / TimeSpan.TicksPerMillisecond) > (lastUpdate_ms + UPDATE_INTERVAL))
        {
            System.Threading.Tasks.Task.Factory.StartNew(() => { update(); });
            return true;
        }
        else return false;
    }


    }