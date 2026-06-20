import re
import sys
import subprocess
import os

def run_cmd(args):
    try:
        result = subprocess.run(args, capture_output=True, text=True, check=True)
        return result.stdout.strip()
    except Exception as e:
        print(f"Warning/Error running {' '.join(args)}: {e}")
        return ""

def get_next_version(current_file_name):
    # 1. Fetch tags from remote to ensure we have all tags from GitHub
    run_cmd(["git", "fetch", "--tags", "--force"])
    
    # 2. Get list of tags
    tags_output = run_cmd(["git", "tag", "-l"])
    tags = [t.strip() for t in tags_output.split("\n") if t.strip()]
    
    version_pattern = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
    parsed_versions = []
    
    for tag in tags:
        match = version_pattern.match(tag)
        if match:
            major, minor, patch = map(int, match.groups())
            parsed_versions.append((major, minor, patch))
            
    if parsed_versions:
        # Find the highest version tag
        latest_version = max(parsed_versions)
        major, minor, patch = latest_version
        v_next = (major, minor, patch + 1)
        next_name = f"{v_next[0]}.{v_next[1]}.{v_next[2]}"
        print(f"Found latest GitHub tag: v{major}.{minor}.{patch}. Next version name: {next_name}")
        return next_name
    else:
        print("No semantic vX.Y.Z tags found on remote. Bumping based on file version.")
        # Clean the name (e.g., if there's a leading 'v')
        clean_name = current_file_name
        has_v_prefix = False
        if clean_name.lower().startswith('v'):
            clean_name = clean_name[1:]
            has_v_prefix = True

        parts = clean_name.split('.')
        if len(parts) == 1:
            new_parts = [parts[0], "0", "1"]
        elif len(parts) == 2:
            new_parts = [parts[0], parts[1], "1"]
        else:
            new_parts = list(parts)
            try:
                last_num = int(new_parts[-1])
                new_parts[-1] = str(last_num + 1)
            except ValueError:
                new_parts.append("1")

        next_name = ".".join(new_parts)
        if has_v_prefix:
            next_name = "v" + next_name
        print(f"Bumping file version name {current_file_name} -> {next_name}")
        return next_name

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

    # Match versionName
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if not name_match:
        print("Error: versionName not found in app/build.gradle.kts")
        sys.exit(1)

    current_name = name_match.group(1)

    # Get next version name
    new_name = get_next_version(current_name)

    # Calculate next versionCode based on commit count & current code
    commit_count_str = run_cmd(["git", "rev-list", "--count", "HEAD"])
    commit_count = 1
    if commit_count_str:
        try:
            commit_count = int(commit_count_str)
        except ValueError:
            pass

    # Ensure code monotonically increases starting from a base
    new_code = max(current_code + 1, commit_count)
    print(f"Bumping Code: {current_code} -> {new_code}")

    # Write to GITHUB_ENV if running in GitHub Actions
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
