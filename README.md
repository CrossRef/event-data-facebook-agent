# Event Data Facebook Agent

Very early prototype of Event Data Facebook Agent.


# Status

R&D as of August 2016

# To run

    time lein run «facebook-token» «url-file» «output-file» «source-token»

 - `facebook-token` is the auth token (todo oauth)
 - `url-file` is a file from Thamnophilus alternating (DOI \n URL \n)
 - `output-file` is a path to a JSON file which will contain a sequence of evidence log items (which will include deposits)
 - `source-token` is the Lagotto source token


 