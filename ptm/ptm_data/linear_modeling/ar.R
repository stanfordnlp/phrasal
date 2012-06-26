# Arabic PTM data processing
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
# not randomized: 10,11,16,22,23,24,33,34,40,43,45,49,50
#
# (must include these)
# randomized: 61,59,56,55
#
# Javascript broken: 48 (we don't get start/end events)
#
# These are the outliers: 51 (in terms of time)
#

ar.data <- loadptmframe("trans_frame.ar.csv")

ar.data.graph <- ar.data
ar.data.graph$src_id <- as.factor(ar.data.graph$src_id)
ar.data.graph$user_id <- as.factor(ar.data.graph$user_id)
xylowess.fnc(norm_time ~ src_id | user_id, data=ar.data.graph, ylab = "norm time")

# Filter out the outliers for lowess plot
# Notin operator
ar.data.graph <- ar.data.graph[ar.data.graph$user_id %ni% c(51),]
xylowess.fnc(norm_time ~ src_id | user_id, data=ar.data.graph, ylab = "norm time")

#
# Iterate over subsets of users
#

# Users that we always want to include
perm_ids <- c(61,59,56,55)

# Matrix of users for every model fitting iteration
user_ids <- combn(c(10,11,16,22,23,24,33,34,40,43,45,49,50), 4)

for (i in 1:ncol(user_ids)){ 
  itr_users <- c(user_ids[,i], perm_ids)
  print(itr_users)

  test.subset <- subset(ar.data, user_id %in% itr_users)
  test.subset$src_id <- factor(test.subset$src_id, labels="s")
  test.subset$user_id <- factor(test.subset$user_id, labels="u")

  model.data <- subset(test.subset, select=c(norm_time,user_id,src_id,ui_id))

  # Center the response so that the co-effecients are
  # more interpretable.
  model.data$norm_time <- scale(model.data$norm_time, center=T)

  # Fit model A
  fitmix.A(model.data)
}
