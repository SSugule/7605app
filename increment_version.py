import re
import sys

def increment_version():
    filepath = "app/build.gradle.kts"
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading file: {e}")
        sys.exit(1)

    # Match versionCode
    code_match = re.search(r"versionCode\s*=\s*(\d+)", content)
    if not code_match:
        print("Error: versionCode not found in app/build.gradle.kts")
        sys.exit(1)
    
    current_code = int(code_match.group(1))
    new_code = current_code + 1

    # Match versionName
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if not name_match:
        print("Error: versionName not found in app/build.gradle.kts")
        sys.exit(1)

    current_name = name_match.group(1)
    
    # Clean the name (e.g., if there's a leading 'v')
    clean_name = current_name
    has_v_prefix = False
    if clean_name.lower().startswith('v'):
        clean_name = clean_name[1:]
        has_v_prefix = True

    parts = clean_name.split('.')
    if len(parts) == 1:
        # e.g., "1" -> "1.0.1"
        new_parts = [parts[0], "0", "1"]
    elif len(parts) == 2:
        # e.g., "1.0" -> "1.0.1"
        new_parts = [parts[0], parts[1], "1"]
    else:
        # e.g., "1.0.5" -> "1.0.6"
        new_parts = list(parts)
        try:
            last_num = int(new_parts[-1])
            new_parts[-1] = str(last_num + 1)
        except ValueError:
            # If last part is not an integer (e.g., "1.0.0-beta"), append .1
            new_parts.append("1")

    new_name = ".".join(new_parts)
    if has_v_prefix:
        new_name = "v" + new_name

    print(f"Incrementing version: Code {current_code} -> {new_code}, Name {current_name} -> {new_name}")

    # Write to GITHUB_ENV if running in GitHub Actions
    import os
    if "GITHUB_ENV" in os.environ:
        try:
            with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as ge:
                ge.write(f"NEW_VERSION_NAME={new_name}\n")
                ge.write(f"NEW_VERSION_CODE={new_code}\n")
            print("Successfully written to GITHUB_ENV")
        except Exception as e:
            print(f"Warning: Could not write to GITHUB_ENV: {e}")

    # Replace in content
    content = re.sub(r"(versionCode\s*=\s*)\d+", f"\\g<1>{new_code}", content)
    content = re.sub(r'(versionName\s*=\s*")[^"]+(")', f"\\g<1>{new_name}\\g<2>", content)

    try:
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        print("Successfully updated app/build.gradle.kts")
    except Exception as e:
        print(f"Error writing file: {e}")
        sys.exit(1)

if __name__ == "__main__":
    increment_version()
