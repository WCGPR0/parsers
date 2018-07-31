Dim fso : Set fso = CreateObject("Scripting.FileSystemObject")
Dim shell : Set shell = CreateObject("WScript.Shell")


yesterday = DATE() - 1
path = '* PATH OF DAILYDATAGETTER OUTPUT
path_output = "\\" & formatDate(BusinessDateAdd(-1,Date())) & ".pdf"

WScript.Echo "Looking for: " & path
If (fso.FileExists(path)) Then
	WScript.Echo("File Exists!")
	Upload path, path_output
	WScript.Echo("File Uploaded!")
Else
	WScript.Echo("File does not Exist!")
	EmailAlert("FUTURES")
End If


Function formatDate(myDate)
	d = paddDate(Day(myDate))
	m = paddDate(Month(myDate))
	y = paddDate(Year(myDate))
	formatDate = y & "_" & m & "_" & d
End Function

Function paddDate(num)
	If(Len(num)=1) Then
		paddDate= "0" & num
	Else
		paddDate=num
	End If
End Function

Function BusinessDayAdd(delta, dt)
  dim weeks, days, day
  
  weeks = Fix(delta/5)
  days = delta Mod 5
  day = DatePart("w",dt)
  
  If (day = 7) And days > -1 Then
	If days = 0 Then
	  days = days - 2
	  day = day + 2
	End If
	days = days + 1
	day = day - 7
  End If
  If day = 1 And days < 1 Then
	If days = 0 Then
	  days = days + 2
	  day = day - 2
	End If
	days = days - 1
	day = day + 6
  End If
  If day + days > 6 Then days = days + 2
  If day + days < 2 Then days = days - 2
  
  BusinessDayAdd = DateAdd("d", (weeks * 7 + days), dt)
  
End Function


Sub Upload(path,path_output)
	If fso.FileExists(DestinationFile) Then
    If Not fso.GetFile(DestinationFile).Attributes And 1 Then
					fso.CopyFile path, path_output, True
		Else
		'The file is read-only.
		'Remove the read-only attribute
            fso.GetFile(path_output).Attributes = fso.GetFile(path_output).Attributes - 1
            'Replace the file
            fso.CopyFile path, path_output, True
		End If
	Else
			fso.CopyFile path, path_output, True
	End If
End Sub


Sub EmailAlert(market)
Set MyEmail=CreateObject("CDO.Message")
MyEmail.Subject="EN - Parser: ICE Source Document Missing"
MyEmail.From=""
MyEmail.To= '* DESTINATION OF EMAIL RECEPIENTS
MyEmail.TextBody="The (" & market & ") ICE Report is missing. Please manually download ICE documents. Thank you."
MyEmail.Configuration.Fields.Item ("http://schemas.microsoft.com/cdo/configuration/sendusing")=2

'SMTP Server
MyEmail.Configuration.Fields.Item ("http://schemas.microsoft.com/cdo/configuration/smtpserver")=""

'SMTP Port
MyEmail.Configuration.Fields.Item ("http://schemas.microsoft.com/cdo/configuration/smtpserverport")=25

MyEmail.Configuration.Fields.Update
MyEmail.Send
set MyEmail=nothing

End Sub
