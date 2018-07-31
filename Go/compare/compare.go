package main

import (
  "io/ioutil"
  "os"
  "strings"
  "fmt"
  "regexp"
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

func main() {
    re := regexp.MustCompile(`\r?\n`)
    var delim = "\n"
    if (len(os.Args) > 3) {
      delim = os.Args[3]
    }
    var output = ""

    var m map[string]string
    m = make(map[string]string)

    dat := strings.Split(MustString(ioutil.ReadFile(re.ReplaceAllString(os.Args[1], "\n"))), delim)
    dat2 := strings.Split(MustString(ioutil.ReadFile(re.ReplaceAllString(os.Args[2], "\n"))), delim)
    for _, k := range dat {
      temp := strings.Split(k, ",")
      m[temp[0]] = temp[1]
    }

      for j := 0; j < len(dat2); j++ {
        temp := strings.Split(dat2[j],",")
        if val, ok := m[temp[0]]; ok {
          if (val != temp[1]) {
            output += fmt.Sprintf("%s,%s,%s\n", temp[0], val, temp[1])
          }
        } else {
            output += fmt.Sprintf("%s,%s,%s\n", temp[0], val, temp[1])
        }
      }
    d1 := []byte(output)
    err := ioutil.WriteFile("output", d1, 0644)
    _ = err
}
