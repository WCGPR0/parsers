namespace SDRDataParserGUI.Models
{
    using System;
    using System.Collections.Generic;
    using System.Data;
    using System.Data.Entity;
    using System.Linq;
    using System.Web.Mvc;

    public class GraphData : DbContext
    {
        public struct Transaction
        {
            public string report_time { get; set; }
            public string contract_name { get; set; }
            public string source { get; set; }
            public string type { get; set; }
            public string start_date { get; set; }
            public string end_date { get; set; }
            public string exec_time { get; set; }
            public string strategy { get; set; }
            public double total_volume { get; set; }
            public double daily_volume { get; set; }
            public double price { get; set; }
            public double premium { get; set; }
            public double strike { get; set; }
            public string unit { get; set; }
            public string status { get; set; }

            public Transaction(Transaction transaction)
            {
                report_time = transaction.report_time;
                contract_name = transaction.contract_name;
                source = transaction.source;
                type = transaction.type;
                start_date = transaction.start_date;
                end_date = transaction.end_date;
                exec_time = transaction.exec_time;
                strategy = transaction.strategy;
                total_volume = transaction.total_volume;
                daily_volume = transaction.daily_volume;
                price = transaction.price;
                premium = transaction.premium;
                strike = transaction.strike;
                unit = transaction.unit;
                status = transaction.status;
            }
            public Transaction(FormCollection transaction)
            {
                report_time = transaction["report_time"];
                contract_name = transaction["contract_name"];
                source = transaction["source"];
                type = transaction["type"];
                start_date = transaction["start_date"];
                end_date = transaction["end_date"];
                exec_time = transaction["exec_time"];
                strategy = transaction["strategy"];
                double total_volume_, daily_volume_, price_, premium_, strike_;
                total_volume = Double.TryParse(transaction["total_volume"], out total_volume_) ? Convert.ToDouble(total_volume_) : 0;
                daily_volume = Double.TryParse(transaction["daily_volume"], out daily_volume_) ? Convert.ToDouble(daily_volume_) : 0;
                price = Double.TryParse(transaction["price"], out price_) ? Convert.ToDouble(price_) : 0;
                premium = Double.TryParse(transaction["premium"], out premium_) ? Convert.ToDouble(premium_) : 0;
                strike = Double.TryParse(transaction["strike"], out strike_) ? Convert.ToDouble(strike_) : 0;
                unit = transaction["unit"];
                status = transaction["status"];
            }
        }

        public int ICE_COUNT { get; set; }
        public int DTCC_COUNT { get; set; }

        public List<Transaction> reportList { get; set; }

        public GraphData()
            : base("name=GraphData")
        {
            reportList = new List<Transaction>();
            clear();
        }

        public void add(Transaction transaction)
        {
            reportList.Add(transaction);
            if (transaction.source.Contains("ICE"))
                    ++ICE_COUNT;
            else if (transaction.source.Contains("DTCC"))
                ++DTCC_COUNT;
        }

        public void clear ()
        {
            reportList.Clear();
            ICE_COUNT = 0;
            DTCC_COUNT = 0;
        }

        public string[] getName()
        {
            Type type = typeof(Transaction);
            string[] ret = new string[type.GetFields().Length];
            foreach (var item in type.GetFields().Select((value, i) => new { i, value }))
            {
                ret[item.i] = ((NameAttribute)item.value.GetCustomAttributes(false)[0]).name;
            }
            return ret;
        }

    }


}