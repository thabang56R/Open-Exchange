def commit_callback(commit, metadata):
    # Rewrite Lovable bot
    if commit.author_name.lower().startswith("lovable") or commit.author_email.endswith("@lovable.dev"):
        commit.author_name = "Thabang Rakeng"
        commit.author_email = "thabang56@hotmail.com"

    if commit.committer_name.lower().startswith("lovable") or commit.committer_email.endswith("@lovable.dev"):
        commit.committer_name = "Thabang Rakeng"
        commit.committer_email = "thabang56@hotmail.com"

    # Rewrite gpt-engineer-app bot
    if commit.author_name == "gpt-engineer-app[bot]" or "gpt-engineer-app[bot]" in commit.author_email:
        commit.author_name = "Thabang Rakeng"
        commit.author_email = "thabang56@hotmail.com"

    if commit.committer_name == "gpt-engineer-app[bot]" or "gpt-engineer-app[bot]" in commit.committer_email:
        commit.committer_name = "Thabang Rakeng"
        commit.committer_email = "thabang56@hotmail.com"
