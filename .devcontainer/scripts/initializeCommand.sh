#!/bin/bash

envPath=".devcontainer/.env"
pomPath="./pom.xml"
NS="http://maven.apache.org/POM/4.0.0"

# Function to extract a value from pom.xml using an XPath expression
get_value_from_pom() {
  local xpath="$1"
  local value
  value=$(xmlstarlet sel -N ns="$NS" -t -m "$xpath" -v '.' -n "$pomPath")
  echo "$value"
}

# Function to write a key=value pair into the .env file if the value is not empty
write_to_env() {
  local key="$1"
  local value="$2"
  if [[ -n "$value" ]]; then
    echo "${key}=${value}" >> "$envPath"
  fi
}

# Special case: write both full and major version of coe.version into .env
write_coe_version() {
  local xpath="/ns:project/ns:properties/ns:coe.version"
  local value
  value=$(get_value_from_pom "$xpath")
  if [[ -n "$value" ]]; then
    write_to_env "CANVAS_BASE_IMAGE_VERSION" "$value"
    # Extract major version (part before the first dot)
    write_to_env "CANVAS_BASE_IMAGE_VERSION_MAJOR" "${value%%.*}"
  fi
}

# Write revision property, substituting ${changelist} placeholder with actual changelist value using envsubst
write_project_version() {
  local revision_xpath="/ns:project/ns:properties/ns:revision"
  local revision
  revision=$(get_value_from_pom "$revision_xpath")

  local changeList_xpath="/ns:project/ns:properties/ns:changelist"
  local changeList
  changeList=$(get_value_from_pom "$changeList_xpath")

  if [[ -n "$revision" ]]; then
    # Default changelist to empty string if it is not set
    changeList="${changeList:-}"

    # Export changelist variable so envsubst can substitute it
    export changelist="$changeList"

    # Substitute ${changelist} in revision with actual changelist value
    local resolved_revision
    resolved_revision=$(envsubst <<< "$revision")

    write_to_env "PROJECT_VERSION" "$resolved_revision"

    # Cleanup exported variable
    unset changelist
  fi
}

main() {
  # Clear existing .env file before writing
  > "$envPath"

  # For volume binding in docker-compose-test.yaml we need the absolute path of the workspace on the host
  write_to_env "WORKSPACE_PATH_ON_HOST" "`pwd`"

  # Extract project name and write to .env
  local project_name
  project_name=$(get_value_from_pom "/ns:project/ns:name")
  write_to_env "PROJECT_NAME" "$project_name"

  # Handle coe.version property separately
  write_coe_version

  # Extract changelist and write to .env
  local changelist product projectkey
  changelist=$(get_value_from_pom "/ns:project/ns:properties/ns:changelist")
  write_to_env "changelist" "$changelist"

  # Write revision property with changelist substitution
  write_project_version

  # Extract product and write to .env
  product=$(get_value_from_pom "/ns:project/ns:properties/ns:product")
  write_to_env "product" "$product"

  # Extract projectkey and write to .env
  projectkey=$(get_value_from_pom "/ns:project/ns:properties/ns:projectkey")
  write_to_env "projectkey" "$projectkey"
}

main
