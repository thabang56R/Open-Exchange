def commit_callback(commit, metadata):
    # Normalize to lowercase for matching
    author_name = commit.author_name.lower()
    author_email = commit.author_email.lower()
    committer_name = commit.committer_name.lower()
    committer_email = commit.committer_email.lower()

    # Rewrite Lovable
    if "lovable" in author_name or "lovable" in author_email:
        commit.author_name = "Thabang Rakeng"
        commit.author_email = "thabang56@hotmail.com"
    if "lovable" in committer_name or "lovable" in committer_email:
        commit.committer_name = "Thabang Rakeng"
        commit.committer_email = "thabang56@hotmail.com"

    # Rewrite gpt-engineer-app
    if "gpt-engineer-app" in author_name or "gpt-engineer-app" in author_email:
        commit.author_name = "Thabang Rakeng"
        commit.author_email = "thabang56@hotmail.com"
    if "gpt-engineer-app" in committer_name or "gpt-engineer-app" in committer_email:
        commit.committer_name = "Thabang Rakeng"
        commit.committer_email = "thabang56@hotmail.com"

