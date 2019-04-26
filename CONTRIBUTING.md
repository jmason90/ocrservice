
Hi

I realize it's been awhile since you have made changes to this project.

Testing the OCR service, it fails on curl with a bad curl command format error ( see below )
Is there a recent valid curl command format to do the OCR in English correctly?
Thanks for your help!

Jim Mason

-----------------------------
curl results

curl  -v  -F "dpi=300"   -F "lang=eng"   -F "bc-concepts-p1-s1-Capture.PNG" http://35.164.84.230:8081/oc

Warning: Illegally formatted input field!
curl: option -F: is badly used here
curl: try 'curl --help' for more information


-------------------------------
curl --version
curl 7.55.1 (Windows) libcurl/7.55.1 WinSSL
Release-Date: [unreleased]
Protocols: dict file ftp ftps http https imap imaps pop3 pop3s smtp smtps telnet tftp
Features: AsynchDNS IPv6 Largefile SSPI Kerberos SPNEGO NTLM SSL
