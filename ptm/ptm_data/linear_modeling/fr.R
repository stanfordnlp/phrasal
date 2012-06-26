# Mike Lawrence's sensible interface to both aov and lme4
# This package loads lme4
library(ez)

# Baayen's language shit from his 2008 book
# We need this for pvals.fnc()
library(languageR)

# Load the source frame
d.src <- read.csv("source_frame.csv",header=T)
#d.src$src_id <- as.factor(d.src$src_id)
ezPrecis(d.src)

# Load the target frame
d.tgt <- read.csv("trans_frame.fr.csv",header=T)
#d.tgt$src_id <- as.factor(d.tgt$src_id)
#d.tgt$user_id <- as.factor(d.tgt$user_id)
ezPrecis(d.tgt)

# User data
d.user <- read.csv("user_frame.csv",header=T)
#d.user$user_id <- as.factor(d.user$user_id)
#d.user$en_level <- as.factor(d.user$en_level)
#d.user$birth_country <- as.factor(d.user$birth_country)
#d.user$home_country <- as.factor(d.user$home_country)
ezPrecis(d.user)

# Merge these three frames
d.tmp1 <- merge(d.src,d.tgt,by.x = "src_id", by.y = "src_id")
fr.data <- merge(d.tmp1,d.user,by.x = "user_id", by.y = "user_id")

# To subset some users
#
fr.data.graph <- fr.data
fr.data.graph$src_id <- as.factor(fr.data.graph$src_id)
fr.data.graph$user_id <- as.factor(fr.data.graph$user_id)
xylowess.fnc(norm_time ~ src_id | user_id, data=fr.data.graph, ylab = "norm time")

# Notin operator
#`%ni%` <- Negate(`%in%`) 
#fr.data.graph <- fr.data.graph[fr.data.graph$user_id %ni% c(10,33,51),]
xylowess.fnc(norm_time ~ src_id | user_id, data=fr.data.graph, ylab = "norm time")

#
# Now select a subset of the users for linear modeling.
#
#test.subset <- subset(fr.data, user_id %in% c(10,11,16,22,61,59,56,55))
#test.subset$src_id <- as.factor(test.subset$src_id)
#test.subset$user_id <- as.factor(test.subset$user_id)

#model.data <- subset(test.subset, select = c(time,user_id,src_id,ui_id))

#lmm.1 <- ezMixed(model.data, dv = .(time), fixed = .(ui_id), random = .(src_id,user_id), return_models=T)

# Sample the p-values
#lmm.pv <- pvals.fnc(lmm.1$models$ui_id$unrestricted)
