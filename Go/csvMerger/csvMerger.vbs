Set objFSO = CreateObject("Scripting.FileSystemObject")
Set objDict = CreateObject("Scripting.Dictionary")

Const delim = ","

WScript.Echo "<!--Starting Program-->"

REM  <!-- <Setting up Header> -->

for x = 0 To WScript.Arguments.Count - 1
WScript.Echo "Setting up column headers (" & WScript.Arguments(x) & ")..."
  Set inputFile = objFSO.OpenTextFile(WScript.Arguments(x))
  line = Split(inputFile.ReadLine, delim)
  for each y in line
    If Not(objDict.Exists(y)) Then
      WScript.Echo "Adding column:" & vbTab & y
      objDict.Add y, "true"
    End If
  next
  inputFile.Close
next

REM  <!-- </Setting up Header> -->

REM <!-- <Merging csv> -->

WScript.Echo "Outputting file to:" & vbTab & objFSO.GetBaseName(WScript.ScriptFullName) & ".csv"
Set outputFile = objFSO.CreateTextFile(objFSO.GetBaseName(WScript.ScriptFullName) & ".csv", True)

Dim objDict_it: objDict_it = objDict.Keys

Dim outputHeader: outputHeader = ""
for y = 0 To (objDict.Count - 1)
  outputHeader = outputHeader & objDict_it(y)
  If y <> (objDict.Count - 1) Then
    outputHeader = outputHeader & delim
  End If
next
outputFile.WriteLine outputHeader

for x = 0 To WScript.Arguments.Count - 1
Set inputFile = objFSO.OpenTextFile(WScript.Arguments(x))
'Gets the individual header
line = Split(inputFile.ReadLine, delim)

Do Until inputFile.AtEndOfStream
  Dim outputLine: outputLine = ""
  line_= Split(inputFile.ReadLine, delim)
  for y = 0 To (objDict.Count - 1)
    index = in_array(objDict_it(y), line)
    If Not(index = -1) Then
      outputLine = outputLine & line_(index)
    End If
    If y <> (objDict.Count - 1) Then
      outputLine = outputLine & delim
    End If
  next
  outputFile.WriteLine outputLine
Loop

next

REM <!-- </Merging csv> -->

outputFile.Close
WScript.Echo "<!--Terminating Program-->" & vbCrLf & "(press key to finish)"
WScript.StdIn.ReadLine

Function in_array(needle, haystack)
  in_array = -1
  For i = LBound(haystack) To UBound(haystack)
    If haystack(i) = needle Then
      in_array = i
    End If
  Next
End Function
