package main

import (
    "fmt"
  	"net/http"
    "time"
    "os"
    "strconv"
    "errors"
    "bytes"
    "mime/multipart"
    "strings"
    "io/ioutil"
)

const (
    LIMIT = 5
    inputUrl    = "http://2captcha.com/in.php"
    responseUrl = "http://2captcha.com/res.php"
    OK          = "OK"
    notReady    = "CAPCHA_NOT_READY"
    pollingTime = 40 * time.Second
)

type Captcha struct {
	key string
}

type formCreator struct {
	err error
}

func New(key string) (*Captcha, error) {
	if key == "" {
		return nil, errors.New("key should not be empty")
	}
	return &Captcha{
		key: key,
	}, nil
}

func getValueOK(body string) (string, error) {
	if strings.Contains(body, "OK|") {
		return strings.Split(body, "|")[1], nil
	}
	return "", errors.New(body)
}


func (captcha *Captcha) createForm(site_key string, page_url string) (*bytes.Buffer, string, error) {
	var buffer bytes.Buffer
	writer := multipart.NewWriter(&buffer)
	defer writer.Close()

	formCreator := &formCreator{}
	formCreator.createFormField("key", captcha.key, writer)
	formCreator.createFormField("googlekey", site_key, writer)
  formCreator.createFormField("pageurl", page_url, writer)
	formCreator.createFormField("method", "userrecaptcha", writer)
	if formCreator.err != nil {
		return nil, "", formCreator.err
	}

	return &buffer, writer.FormDataContentType(), nil
}

func (fc *formCreator) createFormField(fieldName string, fieldValue string, writer *multipart.Writer) {
	if fc.err != nil {
		return
	}
	fw, err := writer.CreateFormField(fieldName)
	if err != nil {
		fc.err = errors.New(fmt.Sprintf("Error: {Failed to create field %s }", fieldName))
		return
	}
	if _, err := fw.Write([]byte(fieldValue)); err != nil {
		fc.err = errors.New(fmt.Sprintf("Error: {Failed to set %s value}", fieldName))
		return
	}
}

func perfomRequest(request *http.Request) (string, error) {
	client := &http.Client{}
	resp, err := client.Do(request)
	defer func() {
		if resp.Body != nil {
			resp.Body.Close()
		}
	}()

	if err != nil {
		return "", err
	}

	if resp.StatusCode != http.StatusOK {
		return "", errors.New(strconv.Itoa(resp.StatusCode))
	}
	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	return string(body), nil
}


func main() {
    //api_key := "8ad7c23276f3641ebd77eb4b26b43923"
    api_key := "f101330e53d887e222bc05a621ceffb8" // PRODUCTION
    //Valid number of parameters is 3
  	if (len(os.Args) != 3 && len(os.Args) != 4) {
  		fmt.Println("Error: {Invalid number of arguments: " + strconv.Itoa(len(os.Args)) + "; Format is decaptcha [site_key] [page_url] (api_key)}")
  		os.Exit(2)
  	}

    site_key := os.Args[1]
    page_url := os.Args[2]
    if (len(os.Args) == 4 && os.Args[3] != "") {
      fmt.Println(string(os.Args[3]))
      api_key = os.Args[3]
    }

    captcha, _ := New(api_key)

    if (site_key == "" || page_url == "") {
      fmt.Println("Error: {Bad parameters, site_key: " + site_key + "\tpage_url: " + page_url)
      os.Exit(2)
    }

    // Makes POST Request -- submits encoded data
    bf, contentType, err := captcha.createForm(site_key, page_url)

    if err != nil {
      fmt.Println("Error: {Failed to create form}")
    }

    req, err := http.NewRequest("POST", inputUrl, bf)
    if err != nil {
      fmt.Println("Error: {Failed to create request/post}")
    }

    req.Header.Set("Content-Type", contentType)
    body, err := perfomRequest(req)
	  if err != nil {
        fmt.Println("Error: {" + err.Error() + "}")
	  }

    captchaID, err := getValueOK(body)
    if err != nil {
        fmt.Println("Error: {" + err.Error() + "}")
	  }  else {
        fmt.Println("Success: {captchaID: " + captchaID + "}")
    }

    body_ := notReady
    // Makes GET Request -- retrieves decoded data
    for tries := 0; body_ == notReady && tries < LIMIT; tries++ {
      time.Sleep(pollingTime);
      req_, _ := http.NewRequest("GET", responseUrl, nil)
      q := req_.URL.Query()
      q.Add("action", "get")
	    q.Add("id", captchaID)
      q.Add("key", captcha.key)
      req_.URL.RawQuery = q.Encode()
      body_, err = perfomRequest(req_)
      if err != nil {
    		 fmt.Println("Error: {" + err.Error() + "}")
      }
         fmt.Println("Debug: {notReady. Reattempting: " + strconv.Itoa(tries) + "/" + strconv.Itoa(LIMIT) +"}")
    }

    output, err_ := getValueOK(body_)
    if err_ != nil {
      fmt.Println("Error: {" + err_.Error() + "}")
    } else {
      fmt.Println("DECODE: {" + output + "}")
    }
}
