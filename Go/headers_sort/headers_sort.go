package main

import (
  "bufio"
  "fmt"
  "io/ioutil"
  "strings"
  "os"
  "sort"
)

func main() {
    files, _ := ioutil.ReadDir("./")
    for _, f := range files {
            if (strings.Contains(f.Name(),"Headers")) {
              fmt.Println(f.Name())
              file, _ := os.Open("./" + f.Name())
              var m map[string]string
              m = make(map[string]string)
              scanner := bufio.NewScanner(file)
              for scanner.Scan() {
                if scanner.Text() != "" {
                entry := strings.Split(scanner.Text(),",")
                m[entry[0]] = entry[1]
              }
              }
              var keys []string
              for k := range m {
                keys = append(keys, k)
              }
              sort.Strings(keys)
              for _, k := range keys {
                fmt.Println(k + "," + m[k])
              }
            }
    }
}
