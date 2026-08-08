def commit_callback(commit, metadata):
    if commit.author_name == "lovable-dev[bot]" or commit.author_email == "41898282+lovable-dev[bot]@users.noreply.github.com":
        commit.author_name = "Thabang Rakeng"
        commit.author_email = "thabang@example.com"
