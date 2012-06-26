# Mike Lawrence's sensible interface to both aov and lme4
# This package loads lme4
library(ez)

# Baayen's language shit from his 2008 book
# We need this for pvals.fnc()
library(languageR)

# Load the source frame
d.src <- read.csv("source_frame.csv",header=T)
ezPrecis(d.src)

# Load the target frame
d.tgt <- read.csv("trans_frame.de.csv",header=T)
ezPrecis(d.tgt)

# User data
d.user <- read.csv("user_frame.csv",header=T)
ezPrecis(d.user)

# Merge these three frames
d.tmp1 <- merge(d.src,d.tgt,by.x = "src_id", by.y = "src_id")
de.data <- merge(d.tmp1,d.user,by.x = "user_id", by.y = "user_id")

# Lowess plots for the data
#
# Additional subjects: 53,54,57,60
#
# Outliers: 18,47 (in terms of time)
#
# Others: 3,4,5,6,12,13,14,15,25,28,35,36,42,46
#
de.data <- de.data[de.data$user_id %in% c(3,4,5,6,12,13,14,15,25,28,35,36,42,46,53,54,57,60),]
de.data$src_id <- factor(de.data$src_id, labels="s")
de.data$user_id <- factor(de.data$user_id,labels="u")

xylowess.fnc(norm_time ~ src_id | user_id, data=de.data, ylab = "norm time")

# Center the response (dependent variable)
de.data$norm_time <- scale(de.data$norm_time, center=T)

#
# Pass a numeric column to do Gelman scaling
#
gscale <- function(x){
y <- scale(x, center=T, scale= 2*sd(x))
return(y)
}

# Gelman scaling of these numeric effects
#de.data$norm_syn_complexity <- gscale(de.data$norm_syn_complexity)
de.data$n_entity_tokens <- gscale(de.data$n_entity_tokens)
de.data$tgt_len <- gscale(de.data$tgt_len)
de.data$en_level <- gscale(de.data$en_level)
de.data$en_spell <- gscale(de.data$en_spell)
de.data$en_vocab <- gscale(de.data$en_vocab)
de.data$de_spell <- gscale(de.data$de_spell)
de.data$de_vocab <- gscale(de.data$de_vocab)
de.data$en_skills <- gscale(de.data$en_skills)
de.data$en_usage <- gscale(de.data$en_usage)
de.data$en_ar_trans <- gscale(de.data$en_de_trans)
de.data$hours_per_week <- gscale(de.data$hours_per_week)


# Fit the model
# tgt_len seems to be the only factor that makes any difference
de.lm.A <- glm(norm_time ~ ui_id + user_id + src_id + tgt_len, data = de.data) 

