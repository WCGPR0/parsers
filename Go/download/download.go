package main
import ("net/http"; "io"; "os"; "io/ioutil"; "log")

func main() {

input,err := ioutil.ReadFile(os.Args[1])
if err != nil {
    log.Fatal(err)
}

out, err := os.Create("output")
if err != nil {
    log.Fatal(err)
}
defer out.Close()

resp, err := http.Get(string(input))
defer resp.Body.Close()

n, err := io.Copy(out, resp.Body)
_ = n
_ = err

}
