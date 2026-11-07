# Frontend Module
This folder contains the Android application and other frontend-related assets.

## Crop Advisor with Google Colab

The Crop Advisor runs the forecasting and ranking code from the repository's
`backend_1.ipynb` notebook in Google Colab. Run the notebook from top to bottom,
add an `NGROK_AUTHTOKEN` secret in Colab, and copy the HTTPS tunnel URL and
generated API key from its final cell into the Crop Advisor connection form.
Keep that Colab runtime running while requesting recommendations. Colab
sessions and tunnel URLs are temporary; after reconnecting, use the new URL and
API key.

The Crop Advisor offers two ranking modes. **Climate Resilience** ranks
eligible crops by their suitability across the forecast crop cycle, selecting
the best planting window. **Maximum Profit** ranks crops by historical
risk-unadjusted net return per hectare per crop day and does not filter by
climate suitability. These are historical estimates, not guaranteed returns.

Crop photos are served from Cloudinary and cached on the device. The image
banner and Cloudinary derivative use a consistent 16:9 aspect ratio. By default,
the API expects each asset's public ID to be `crops/<crop_id>` (for example,
`crops/carrot`). Assets with a different public ID can be added to the
`CLOUDINARY_PUBLIC_IDS` override map in the Colab API cell. Set
`CLOUDINARY_CLOUD_NAME` to your Cloudinary cloud name. The API generates
optimized image URLs that preserve the full crop photo inside a consistent
banner, filling extra space with an automatically selected background color.
No Cloudinary API secret is needed. When an image is unavailable, the app shows
a crop-name placeholder.

Before running the notebook, place `crop_requirements_clean.csv`,
`sri_lanka_crop_profit_with_estimates.csv`, and the `crop-economics*.csv` files
in the Drive folder `/content/drive/MyDrive/data_sets`.