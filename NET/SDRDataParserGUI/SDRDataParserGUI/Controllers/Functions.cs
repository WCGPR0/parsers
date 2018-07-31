using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Web;
using System.Xml;

namespace SDRDataParserGUI
{
    public class Functions {

        private static string[] EnergyConnectionDetails;
        private static Methods CreateEnergyMT()
        {
            string[] fields;

            if (EnergyConnectionDetails == null)
            {
                String ConnString = GetConfigSetting("MiddleTier.Energy");
                fields = ConnString.Split(';');
                if (fields.Length != 4)
                {
                    throw new Exception("Invalid App Settings");
                }
                EnergyConnectionDetails = fields;
            }
            else
            {
                fields = EnergyConnectionDetails;
            }

            try
            {


                Methods tm = new Methods();
                tm.ClientConfigHost = fields[0];
                tm.Initialise("EN", fields[1], fields[2], fields[3], "", false, false);
                return tm;
            }
            catch (Exception ex)
            {
                return null;

            }
        }

        public static string GetConfigSetting(string settingName)
        {
            try
            {
                if (System.Configuration.ConfigurationManager.AppSettings[settingName] != null)
                {
                    return System.Configuration.ConfigurationManager.AppSettings[settingName];
                }
                else
                {
                    return "";
                }

            }
            catch (Exception ex)
            {
                return "";
            }

        }

        public static string MTRequest(string requestName, string Args, Boolean useTransform = false)
        {
            string returnVal = "";
            try
            {
                Methods tm = CreateEnergyMT();
                returnVal = tm.Execute(requestName, Args, useTransform, false);
                tm.Terminate();
            }
            catch (Exception ex)
            {
                // BRWLog("MTRequest", "Error", "Exception", ex.Message);
            }

            return returnVal;

        }

        public static string SelectSingleNodeSafe(XmlNode Node, string childName)
        {
            try
            {
                if (Node.SelectSingleNode(childName) != null)
                {
                    return Node.SelectSingleNode(childName).InnerText;
                }
                else
                    return "";

            }
            catch (Exception e)
            {
                throw e;
            }
        }

        public static string getTime(object tzTup_, string format, bool includeName)
        {
            Tuple<TimeZoneInfo, string> tzTup;
            TimeZoneInfo tz;
            string tzName;
            if (tzTup_ != null)
            {
                tzTup = (Tuple<TimeZoneInfo, string>)tzTup_;
                tz = tzTup.Item1;
                tzName = tzTup.Item2;
            }
            else
            {
                tz = TimeZoneInfo.FindSystemTimeZoneById("Eastern Standard Time");
                tzName = "";
            }

            DateTime theTime = TimeZoneInfo.ConvertTime(DateTime.Now, tz);
            string rVal = theTime.ToString(format);
            if (includeName)
                rVal += tzName;

            return rVal;

        }

        public static string EncryptString(string ClearText)
        {

            byte[] clearTextBytes = Encoding.UTF8.GetBytes(ClearText);

            System.Security.Cryptography.SymmetricAlgorithm rijn = SymmetricAlgorithm.Create();

            MemoryStream ms = new MemoryStream();
            byte[] rgbIV = Encoding.ASCII.GetBytes("lbavittzoaofgcqz");
            byte[] key = Encoding.ASCII.GetBytes("rymnwalaspjdbygwagsxhzzcroywhciu");
            CryptoStream cs = new CryptoStream(ms, rijn.CreateEncryptor(key, rgbIV),
             CryptoStreamMode.Write);

            cs.Write(clearTextBytes, 0, clearTextBytes.Length);

            cs.Close();

            return Base32Encoding.ToString(ms.ToArray());
        }

        public static string DecryptString(string EncryptedText)
        {
            return DecryptString(EncryptedText, typeof(Int64));
        }
        public static string DecryptString(string EncryptedText, Type baseOption)
        {
            byte[] encryptedTextBytes;
            if (baseOption == typeof(Int64))
                encryptedTextBytes = Convert.FromBase64String(EncryptedText);
            else
                encryptedTextBytes = Base32Encoding.ToBytes(EncryptedText);

            MemoryStream ms = new MemoryStream();

            System.Security.Cryptography.SymmetricAlgorithm rijn = SymmetricAlgorithm.Create();


            byte[] rgbIV = Encoding.ASCII.GetBytes("lbavittzoaofgcqz");
            byte[] key = Encoding.ASCII.GetBytes("rymnwalaspjdbygwagsxhzzcroywhciu");

            CryptoStream cs = new CryptoStream(ms, rijn.CreateDecryptor(key, rgbIV),
            CryptoStreamMode.Write);

            cs.Write(encryptedTextBytes, 0, encryptedTextBytes.Length);

            cs.Close();

            return Encoding.UTF8.GetString(ms.ToArray());

        }

        /**
         * @author Shane
         * @source https://stackoverflow.com/questions/641361/base32-decoding
         */
        public class Base32Encoding
        {
            public static byte[] ToBytes(string input)
            {
                if (string.IsNullOrEmpty(input))
                {
                    throw new ArgumentNullException("input");
                }

                input = input.TrimEnd('8'); //remove padding characters
                int byteCount = input.Length * 5 / 8; //this must be TRUNCATED
                byte[] returnArray = new byte[byteCount];

                byte curByte = 0, bitsRemaining = 8;
                int mask = 0, arrayIndex = 0;

                foreach (char c in input)
                {
                    int cValue = CharToValue(c);

                    if (bitsRemaining > 5)
                    {
                        mask = cValue << (bitsRemaining - 5);
                        curByte = (byte)(curByte | mask);
                        bitsRemaining -= 5;
                    }
                    else
                    {
                        mask = cValue >> (5 - bitsRemaining);
                        curByte = (byte)(curByte | mask);
                        returnArray[arrayIndex++] = curByte;
                        curByte = (byte)(cValue << (3 + bitsRemaining));
                        bitsRemaining += 3;
                    }
                }

                //if we didn't end with a full byte
                if (arrayIndex != byteCount)
                {
                    returnArray[arrayIndex] = curByte;
                }

                return returnArray;
            }

            public static string ToString(byte[] input)
            {
                if (input == null || input.Length == 0)
                {
                    throw new ArgumentNullException("input");
                }

                int charCount = (int)Math.Ceiling(input.Length / 5d) * 8;
                char[] returnArray = new char[charCount];

                byte nextChar = 0, bitsRemaining = 5;
                int arrayIndex = 0;

                foreach (byte b in input)
                {
                    nextChar = (byte)(nextChar | (b >> (8 - bitsRemaining)));
                    returnArray[arrayIndex++] = ValueToChar(nextChar);

                    if (bitsRemaining < 4)
                    {
                        nextChar = (byte)((b >> (3 - bitsRemaining)) & 31);
                        returnArray[arrayIndex++] = ValueToChar(nextChar);
                        bitsRemaining += 5;
                    }

                    bitsRemaining -= 3;
                    nextChar = (byte)((b << bitsRemaining) & 31);
                }

                //if we didn't end with a full char
                if (arrayIndex != charCount)
                {
                    returnArray[arrayIndex++] = ValueToChar(nextChar);
                    while (arrayIndex != charCount) returnArray[arrayIndex++] = '8'; //padding
                }

                return new string(returnArray);
            }

            private static int CharToValue(char c)
            {
                int value = (int)c;

                //65-90 == uppercase letters
                if (value < 91 && value > 64)
                {
                    return value - 65;
                }
                //50-55 == numbers 2-7
                if (value < 56 && value > 49)
                {
                    return value - 24;
                }
                //97-122 == lowercase letters
                if (value < 123 && value > 96)
                {
                    return value - 97;
                }

                throw new ArgumentException("Character is not a Base32 character.", "c");
            }

            private static char ValueToChar(byte b)
            {
                if (b < 26)
                {
                    return (char)(b + 65);
                }

                if (b < 32)
                {
                    return (char)(b + 24);
                }

                throw new ArgumentException("Byte is not a value Base32 value.", "b");
            }

        }
    }
}
 