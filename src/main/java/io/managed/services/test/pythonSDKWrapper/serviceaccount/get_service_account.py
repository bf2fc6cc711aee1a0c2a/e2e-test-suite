import argparse
import os
import time
import rhoas_service_accounts_mgmt_sdk
from rhoas_service_accounts_mgmt_sdk.api import service_accounts_api
from rhoas_service_accounts_mgmt_sdk.model.service_account_data import ServiceAccountData
from rhoas_service_accounts_mgmt_sdk.model.error import Error
from rhoas_service_accounts_mgmt_sdk.model.red_hat_error_representation import RedHatErrorRepresentation
from pprint import pprint
import dotenv
import auth.rhoas_auth as auth

parser = argparse.ArgumentParser()
parser.add_argument('--service_account_id', help='The ID of the Kafka instance', required=True)
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

# Enter a context with an instance of the API client
with rhoas_service_accounts_mgmt_sdk.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = service_accounts_api.ServiceAccountsApi(api_client)
    id =  args.service_account_id# str | 

    # example passing only required values which don't have defaults set
    try:
        # Get service account by id
        api_response = api_instance.get_service_account(id)
        pprint(api_response)
    except rhoas_service_accounts_mgmt_sdk.ApiException as e:
        print("Exception when calling ServiceAccountsApi->get_service_account: %s\n" % e)