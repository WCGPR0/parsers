using Google.Apis.Auth.OAuth2;
using Google.Apis.Drive.v3;
using Google.Apis.Drive.v3.Data;
using Google.Apis.Services;
using Google.Apis.Util.Store;
using Microsoft.Win32;
using System;
using System.IO;
using System.Threading;

namespace ConstantContactSendTrades.API.GoogleDrive
{
    public class GoogleDriveAPI
    {
        // If modifying these scopes, delete your previously saved credentials
        // at ~/.credentials/drive-dotnet-quickstart.json
        static string[] Scopes = { DriveService.Scope.DriveFile };
        static string ApplicationName = "GDrive API";
        DriveService service;
        public GoogleDriveAPI()
        {
            UserCredential credential;
            using (var stream =
            new FileStream(Path.Combine(GlobalFunctions.getInstallPath(),"credentials.json"), FileMode.Open, FileAccess.Read))
            {
                // The file token.json stores the user's access and refresh tokens, and is created
                // automatically when the authorization flow completes for the first time.
                string credPath = Path.Combine(GlobalFunctions.getEnvironmentPath(), "", this.GetType().Name, "token.json");
                credential = GoogleWebAuthorizationBroker.AuthorizeAsync(
                    GoogleClientSecrets.Load(stream).Secrets,
                    Scopes,
                    "user",
                    CancellationToken.None,
                    new FileDataStore(credPath, true)).Result;
                GlobalFunctions.InfoLog("Credential file saved to: " + credPath);
            }

            // Create Drive API service.
            service = new DriveService(new BaseClientService.Initializer()
            {
                HttpClientInitializer = credential,
                ApplicationName = ApplicationName,
            });
        }
        public string upload(string filePath)
        {
            string fileID = uploadFile(filePath);
            if (String.IsNullOrEmpty(fileID)) return "";
            GlobalFunctions.DebugLog($"Successfully uploaded file: {fileID}; retrieving public link");
            var request = service.Files.Get(fileID);
            request.Fields = "webContentLink";
            var file = request.Execute();
            return file.WebContentLink;
        }
        private string uploadFile(string filePath)
        {
            string fileID = String.Empty;
            FileInfo fileInfo = new FileInfo(filePath);
            var fileMetadata = new Google.Apis.Drive.v3.Data.File()
            {
                Name = fileInfo.Name
            };
            using (var stream = new System.IO.FileStream(filePath, System.IO.FileMode.Open, System.IO.FileAccess.Read, FileShare.ReadWrite))
            {
                var request = service.Files.Create(fileMetadata, stream, GetMimeType(fileInfo));
                if (request == null) return fileID;
                GlobalFunctions.DebugLog($"Successfully created file: {fileInfo.Name}");

                request.Fields = "id";
                request.Upload();

                GlobalFunctions.DebugLog($"Successfully queried file: {fileInfo.Name}");

                var file = request.ResponseBody;

                if (file == null) return fileID;

                fileID = file?.Id;

                if (String.IsNullOrEmpty(fileID))
                    throw new Exception("Error uploading file; no file ID returned");
                else
                    GlobalFunctions.InfoLog("File ID: " + fileID);

                Permission perm = new Permission();
                perm.Type = "anyone";
                perm.Role = "reader";
                service.Permissions.Create(perm, fileID).Execute();
            }
            return fileID;
        }

        private string GetMimeType(FileInfo fileInfo)
        {
            string mimeType = "application/unknown";
            RegistryKey regKey = Registry.ClassesRoot.OpenSubKey(
                fileInfo.Extension.ToLower()
            );
            if (regKey != null)
            {
                object contentType = regKey.GetValue("Content Type");

                if (contentType != null)
                    mimeType = contentType.ToString();
            }
            return mimeType;

        }
    }
}
