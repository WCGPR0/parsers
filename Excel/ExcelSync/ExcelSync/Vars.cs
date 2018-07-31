using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.IO;
using System.Threading.Tasks;

namespace ExcelSync
{
    class Vars
    {
        public static string AssemblyDirectory
        {
            get
            {
                string codeBase = System.Reflection.Assembly.GetExecutingAssembly().CodeBase;
                UriBuilder uri = new UriBuilder(codeBase);
                string path = Uri.UnescapeDataString(uri.Path);
                return Path.GetDirectoryName(path);
            }
        }
        public static String SessionID = Guid.NewGuid().ToString();
        public static String UserID = "";
        public static DateTime LatestUpdateTime = DateTime.Now.AddMinutes(-10);
    }
}
