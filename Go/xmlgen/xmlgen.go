/** Revamped version
    xmlgen.go version 0.01
    Reads in an csv file and generates the parameters in the form of request.getxml */

package main

import (
  "os"
  "io"
  "fmt"
  "encoding/csv"
  "sync"
  "sync/atomic"
  "strconv"
)


func check(e error) {
    if e != nil {
        panic(e)
    }
}

func MustString(i []byte, err error) string {
    if err != nil {
        panic(err)
    }
    return string(i)
}

/** Global variables & Default Values */
var templateString = ``;
const maxLinesPerFile uint64 = 25000
const initialFileName string = "output"

func main() {
    argsWithoutProg := os.Args[1:]
    if (len(argsWithoutProg) == 2) {
      templateString = argsWithoutProg[1]
      fmt.Println("Overriding templateString with: " + templateString)
    }
    if (len(argsWithoutProg) > 2) {
      panic("Invalid number of arguments!")
    }
    fileName := argsWithoutProg[0]
    fmt.Println("Running program with input file: " + fileName)
    channel := parseFile(fileName)

    /*header := <- channel
    _ = header*/
    var lineNumber uint64 = maxLinesPerFile
    var fileNumber uint64 = 0
    var file *os.File
    var mutex = &sync.Mutex{}
    fmt.Printf("Program initialized.")
    for line := range channel {
      atomic.AddUint64(&lineNumber, 1)
      fmt.Printf("Running Line " + strconv.Itoa(int(lineNumber)))
      if (lineNumber >= maxLinesPerFile) {
        atomic.AddUint64(&fileNumber, 1)
        fileNumber_ := atomic.LoadUint64(&fileNumber)
        mutex.Lock()
          fmt.Printf("Maximum lines " + strconv.Itoa(int(lineNumber)) + " per file exceeded. Writing to new file: output" + strconv.Itoa(int(fileNumber_)) + ".sql")
          lineNumber = 0
        mutex.Unlock()
        if (file != nil) {
          file.WriteString("\r\nend;")
          file.Close()
        }
        var err error
        file, err = os.Create(initialFileName + strconv.Itoa(int(fileNumber_)) + ".sql")
        check(err)
        file.WriteString("declare\r\nxout xmltype;\r\nbegin\r\n")
      }
      formatted_line := fmt.Sprintf(templateString, iface(line)...)
      file.WriteString(formatted_line)
    }
    file.WriteString("\r\nend;")
    file.Close()
}

func iface(list []string) []interface{} {
  vals := make([]interface{}, len(list))
      for i, v := range list { vals[i] = v }
      return vals
}

func parseFile(file string) (ch chan []string) {
  ch = make(chan []string, 10)

  go func() {
  /* <-- Initiialization --> */
  f, err := os.Open(file)
  check(err)
  defer f.Close()
  csvr := csv.NewReader(f)
//  csvr.LazyQuotes = true
/* </-- Initiialization --> */
/* <-- Reading in first Row --> */
/*  header, err := csvr.Read()
  _ = header
  check(err)
/* </-- Reading in first Row --> */


  defer close(ch)

/* <-- Reading in the rest of the Rows --> */
  for {
    row, err := csvr.Read()
    if err != nil {
      if err == io.EOF {
        break
      }
      panic(err)
    }
    ch <- row
  }
/* </-- Reading in the rest of the Rows --> */
}()
return
}
