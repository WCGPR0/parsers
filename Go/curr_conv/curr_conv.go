// go:binary-only-package
package main

import (
 "encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
)

type Rates struct {
	Base       string `json:"base"`
	Date       string `json:"date"`
	Currencies struct {
		AUD float64 `json:"AUD"`
		CAD float64 `json:"CAD"`
		CHF float64 `json:"CHF"`
		EUR float64 `json:"EUR"`
		NZD float64 `json:"NZD"`
		RUB float64 `json:"RUB"`
		JPY float64 `json:"JPY"`
		USD float64 `json:"USD"`
	} `json:"rates"`
}

type 	test struct {
		AUD float64 `json:"AUD"`
	}

var (
	current  string
	err      error
	rates    Rates
	response *http.Response
	body     []byte
)

func main() {

	current = "USD"

	response, err = http.Get("http://api.fixer.io/latest?base=" + current)
	if err != nil {
		fmt.Println(err)
	}
	defer response.Body.Close()

	body, err = ioutil.ReadAll(response.Body)
	if err != nil {
		fmt.Println(err)
	}

	err = json.Unmarshal(body, &rates)
	if err != nil {
		fmt.Println(err)
	}

  fmt.Println("\n==== Currency Rates ====\n")
	fmt.Println("Base:\t", rates.Base)
	fmt.Println("Date:\t", rates.Date)
	fmt.Println("USD=", rates.Currencies.USD)
	fmt.Println("AUD=", rates.Currencies.AUD)
	fmt.Println("CAD=", rates.Currencies.CAD)
	fmt.Println("CHF=", rates.Currencies.CHF)
	fmt.Println("EUR=", rates.Currencies.EUR)
	fmt.Println("RUB=", rates.Currencies.RUB)
	fmt.Println("JPY=", rates.Currencies.JPY)
	fmt.Println("NZD=", rates.Currencies.NZD)
}
