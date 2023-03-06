import argparse
import os
import time
import rhoas_service_accounts_mgmt_sdk
from rhoas_service_accounts_mgmt_sdk.api import service_accounts_api
from rhoas_service_accounts_mgmt_sdk.model.service_account_data import ServiceAccountData
from rhoas_service_accounts_mgmt_sdk.model.error import Error
from rhoas_service_accounts_mgmt_sdk.model.validation_exception_data import ValidationExceptionData
from pprint import pprint
import dotenv
import auth.rhoas_auth as auth


parser = argparse.ArgumentParser()
parser.add_argument('--first', help='The first item', required=False, default=0)
parser.add_argument('--max', help='The max items', required=False, default=100)
parser.add_argument('--env_file', help='The env file', required=False, default='x.config.env')
args = parser.parse_args()

dotenv.load_dotenv(args.env_file)
offline_token = os.environ.get('OFFLINE_TOKEN')

token = {}
token = auth.get_access_token(offline_token)

host_uri = os.environ.get('OBSERVATORIUM_OIDC_ISSUER_URL')
configuration = rhoas_service_accounts_mgmt_sdk.Configuration(
    host = host_uri,
    access_token = token['access_token']
)
with rhoas_service_accounts_mgmt_sdk.ApiClient(configuration) as api_client:
    api_instance = service_accounts_api.ServiceAccountsApi(api_client)
    first = args.first # int |  (optional) if omitted the server will use the default value of 0
    max = args.max # int |  (optional) if omitted the server will use the default value of 20
    try:
        api_response = api_instance.get_service_accounts(first=first, max=max)
        pprint(api_response)
    except rhoas_service_accounts_mgmt_sdk.ApiException as e:
        print("Exception when calling ServiceAccountsApi->get_service_accounts: %s\n" % e)