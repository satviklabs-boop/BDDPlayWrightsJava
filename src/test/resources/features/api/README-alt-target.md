# Secondary API target used to prove the API layer works when reqres.in is
# rate limited (it allows only 40 anonymous requests per day).
#
#   mvn test -DAPI_BASE_URL=https://jsonplaceholder.typicode.com -Dcucumber.filter.tags="@api-smoke"
#
# Then run:  -Dcucumber.filter.tags="@api-smoke"
