# German PTM data processing
#

# Mike Lawrence's sensible interface to both aov and lme4
# This package loads lme4
library(ez)

# Baayen's language shit from his 2008 book
# We need this for pvals.fnc()
library(languageR)

# PTM stuff
source("ptm_utils.R")

# Lowess plots for the data
#
# Additional subjects: 53,54,57,60
#
# Outliers: 18,47 (in terms of time)
#
# Others: 3,4,5,6,12,13,14,15,25,28,35,36,42,46
#

de.data <- loadptmframe("trans_frame.de.csv")

de.data.graph <- de.data
de.data.graph$src_id <- as.factor(de.data.graph$src_id)
de.data.graph$user_id <- as.factor(de.data.graph$user_id)
xylowess.fnc(norm_time ~ src_id | user_id, data=de.data.graph, ylab = "norm time")

# Filter out the outliers for lowess plot
de.data.graph <- de.data.graph[de.data.graph$user_id %ni% c(18,47),]
xylowess.fnc(norm_time ~ src_id | user_id, data=de.data.graph, ylab = "norm time")

#
# Iterate over subsets of users
#

# Users that we always want to include
perm_ids <- c(53,54,57,60)

# Matrix of users for every model fitting iteration
user_ids <- combn(c(3,4,5,6,12,13,14,15,25,28,35,36,42,46), 4)

for (i in 1:ncol(user_ids)){ 
  itr_users <- c(user_ids[,i],perm_ids)
  print(itr_users)

  test.subset <- subset(de.data, user_id %in% itr_users)
  test.subset$src_id <- factor(test.subset$src_id, labels="s")
  test.subset$user_id <- factor(test.subset$user_id, labels="u")

  model.data <- subset(test.subset, select=c(norm_time,user_id,src_id,ui_id))

  # Center the response so that the co-effecients are a bit easier to manage.
  model.data$norm_time <- scale(model.data$norm_time,center=T)

  fitmix.A(model.data)
}
