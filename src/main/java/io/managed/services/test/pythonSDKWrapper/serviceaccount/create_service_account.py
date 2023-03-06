import argparse
import os
import time
import rhoas_service_accounts_mgmt_sdk
from rhoas_service_accounts_mgmt_sdk.api import service_accounts_api
from rhoas_service_accounts_mgmt_sdk.model.service_account_data import ServiceAccountData
from rhoas_service_accounts_mgmt_sdk.model.error import Error
from rhoas_service_accounts_mgmt_sdk.model.red_hat_error_representation import RedHatErrorRepresentation
from rhoas_service_accounts_mgmt_sdk.model.validation_exception_data import ValidationExceptionData
from rhoas_service_accounts_mgmt_sdk.model.service_account_create_request_data import ServiceAccountCreateRequestData
from pprint import pprint
import auth.rhoas_auth as auth


parser = argparse.ArgumentParser()
parser.add_argument('--service_account_name', help='Service Account name', required=True)
parser.add_argument('--service_account_description', help='Service Account description', required=True)
parser.add_argument('--env_file', help='The env file', required=False, default='x.config.env')
args = parser.parse_args()

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
    service_account_create_request_data = ServiceAccountCreateRequestData(
        name=args.service_account_name,
        description=args.service_account_description,
    ) # ServiceAccountCreateRequestData | 'name' and 'description' of the service account
    try:
        api_response = api_instance.create_service_account(service_account_create_request_data)
        pprint(api_response)
        # pprint('jhi')
    except rhoas_service_accounts_mgmt_sdk.ApiException as e:
        print("Exception when calling ServiceAccountsApi->create_service_account: %s\n" % e)